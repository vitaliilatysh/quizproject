"""Exercise adoption, backup privacy and rollback without changing a real stack."""
import contextlib
import copy
import gzip
import importlib.util
import io
import json
import os
from pathlib import Path
import runpy
import secrets
import shutil
import subprocess
import tempfile
import unittest
from unittest.mock import MagicMock, patch
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "deploy/digitalocean/adopt-existing.py"
SPEC = importlib.util.spec_from_file_location("digitalocean_adoption", SOURCE)
ADOPT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(ADOPT)
TEST_ORIGIN = "https://quiz.example.com"
PRIVATE_VALUE = secrets.token_hex(24) + "$'\\# text"


def fixture():
    values = {
        "MYSQL_DATABASE": "quizproject", "MYSQL_USER": "quiz_app",
        "MYSQL_PASSWORD": PRIVATE_VALUE, "MYSQL_ROOT_PASSWORD": PRIVATE_VALUE,
        "REDIS_PASSWORD": PRIVATE_VALUE, "JWT_SECRET": PRIVATE_VALUE,
        "DB_URL": "jdbc:mysql://mysql:3306/quizproject?serverTimezone=UTC",
        "DB_USERNAME": "quiz_app", "DB_PASSWORD": PRIVATE_VALUE,
    }
    containers = []
    for name, image in (("api", "ghcr.io/vitaliilatysh/quizproject"), ("mysql", "mysql:8.4"), ("redis", "redis:8.2.8-alpine")):
        container = {
            "Config": {"Image": image + "@sha256:" + "a" * 64,
                       "Labels": {"com.docker.compose.project": "quizproject", "com.docker.compose.service": name},
                       "Env": [key + "=" + value for key, value in values.items()],
                       "Entrypoint": ["/entrypoint"], "Cmd": ["command", "$REDIS_PASSWORD"],
                       "User": "1000", "WorkingDir": "/app", "StopTimeout": 30},
            "HostConfig": {"RestartPolicy": {"Name": "unless-stopped"}, "ReadonlyRootfs": True,
                           "Memory": 128 * 1024 * 1024, "Init": True, "PidsLimit": 256,
                           "CapDrop": ["ALL"], "SecurityOpt": ["no-new-privileges:true"],
                           "Tmpfs": {"/run/quiz-tmp": "rw"}, "LogConfig": {"Type": "json-file", "Config": {"max-size": "10m"}}},
            "State": {"Running": True}, "Mounts": [],
            "NetworkSettings": {"Networks": {"quizproject_backend": {"Aliases": [name]}},
                                "Ports": {"8081/tcp": [{"HostIp": "127.0.0.1", "HostPort": "8081"}]} if name == "api" else {"3306/tcp": None}},
        }
        if name in ("mysql", "redis"):
            target = "/var/lib/mysql" if name == "mysql" else "/data"
            volume = "quizproject_" + name + "_data"
            container["Mounts"] = [{"Type": "volume", "Name": volume,
                                     "Source": "/var/lib/docker/volumes/" + volume + "/_data", "Destination": target, "RW": True}]
            container["Config"]["Healthcheck"] = {"Test": ["CMD-SHELL", "test -n \"$MYSQL_PASSWORD\""],
                                                    "Interval": 10000000000, "Timeout": 3000000000,
                                                    "StartPeriod": 40000000000, "StartInterval": 1000000000, "Retries": 5}
        containers.append(container)
    return containers


class AdoptionTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)
        self.backend = self.base / "production/backend"
        self.frontend = self.base / "production/frontend"
        for directory in (self.backend / "releases", self.frontend / "releases"):
            directory.mkdir(parents=True)
        self.containers = fixture()
        self.calls = []

    def docker(self, arguments, output=None):
        self.calls.append(arguments)
        if arguments[0] == "inspect":
            return json.dumps(self.containers).encode()
        if arguments[0] == "exec":
            output.write(b"-- MySQL dump\nCREATE TABLE example(id INT);\n-- Dump completed on 2026-11-08\n")
        return b""

    def test_adoption_preserves_credentials_volumes_images_and_rollback_without_restarts(self):
        output = io.StringIO()
        with patch.object(ADOPT, "docker", side_effect=self.docker), contextlib.redirect_stdout(output):
            ADOPT.prepare(self.base, TEST_ORIGIN)
            before = (self.backend / ".env").read_bytes()
            ADOPT.prepare(self.base, TEST_ORIGIN)
        self.assertNotIn(PRIVATE_VALUE, output.getvalue())
        self.assertEqual((self.backend / ".env").read_bytes(), before)
        self.assertIn("MYSQL_DATA_VOLUME='quizproject_mysql_data'", before.decode())
        self.assertIn("MYSQL_DATA_EXTERNAL='true'", before.decode())
        self.assertIn("MYSQL_IMAGE='mysql:8.4@sha256:", before.decode())
        self.assertEqual((self.backend / ".compose-project").read_text(), "quizproject\n")
        release = self.backend / "releases/0-0"
        self.assertEqual((self.backend / "current").resolve(), release)
        rollback = json.loads((release / "compose.yaml").read_text())
        self.assertEqual(rollback["volumes"]["quizproject_mysql_data"], {"name": "quizproject_mysql_data", "external": True})
        self.assertEqual(rollback["services"]["api"]["ports"][0]["host_ip"], "127.0.0.1")
        self.assertEqual(rollback["services"]["mysql"]["pull_policy"], "never")
        self.assertEqual(rollback["services"]["api"]["environment"]["DB_PASSWORD"], PRIVATE_VALUE.replace("$", "$$"))
        for path in (self.backend / ".env", self.frontend / ".env", release / "compose.yaml", release / "image.env"):
            self.assertEqual(path.stat().st_mode & 0o777, 0o600)
        backup = Path(json.loads((self.backend / "adopted.json").read_text())["backup"])
        self.assertEqual(backup.stat().st_mode & 0o777, 0o600)
        with gzip.open(backup, "rb") as handle:
            self.assertIn(b"-- Dump completed on", handle.read())
        self.assertFalse(list(backup.parent.glob("*.sql")))
        self.assertEqual([call[0] for call in self.calls], ["inspect", "compose", "exec"])

    def test_existing_or_partial_files_are_never_overwritten(self):
        (self.backend / ".env").write_text(PRIVATE_VALUE)
        with self.assertRaises(FileExistsError):
            ADOPT.prepare(self.base, TEST_ORIGIN)
        self.assertEqual((self.backend / ".env").read_text(), PRIVATE_VALUE)
        (self.backend / ".env").unlink()
        (self.backend / ".env").symlink_to(self.base / "missing")
        with self.assertRaises(FileExistsError):
            ADOPT.prepare(self.base, TEST_ORIGIN)
        (self.backend / ".env").unlink()
        (self.backend / "adopted.json").write_text("{}")
        with self.assertRaisesRegex(ValueError, "incomplete"):
            ADOPT.prepare(self.base, TEST_ORIGIN)

    def test_rollback_release_cannot_be_overwritten(self):
        (self.backend / "releases/0-0").mkdir()
        with patch.object(ADOPT, "docker", side_effect=self.docker), self.assertRaises(FileExistsError):
            ADOPT.prepare(self.base, TEST_ORIGIN)
        self.assertFalse((self.backend / ".env").exists())

    def test_backup_failure_leaves_credentials_and_current_stack_untouched(self):
        with patch.object(ADOPT, "docker", side_effect=RuntimeError(PRIVATE_VALUE)), self.assertRaises(RuntimeError):
            ADOPT.backup(self.base)
        self.assertFalse(list((self.base / "production/backups").iterdir()))
        with patch.object(ADOPT, "docker", return_value=b""), self.assertRaisesRegex(RuntimeError, "incomplete"):
            ADOPT.backup(self.base)
        self.assertFalse(list((self.base / "production/backups").iterdir()))
        with patch.object(ADOPT, "docker", side_effect=self.docker), \
                patch.object(ADOPT.shutil, "copyfileobj", side_effect=OSError(PRIVATE_VALUE)), self.assertRaises(OSError):
            ADOPT.backup(self.base)
        self.assertFalse(list((self.base / "production/backups").iterdir()))

    def test_partial_installation_is_cleaned_without_removing_the_backup(self):
        writer = ADOPT.private_file

        def fail_frontend(path, content):
            if path == self.frontend / ".env":
                raise OSError(PRIVATE_VALUE)
            writer(path, content)

        with patch.object(ADOPT, "docker", side_effect=self.docker), \
                patch.object(ADOPT, "private_file", side_effect=fail_frontend), \
                contextlib.redirect_stdout(io.StringIO()), self.assertRaises(OSError):
            ADOPT.prepare(self.base, TEST_ORIGIN)
        self.assertFalse((self.backend / ".env").exists())
        self.assertFalse((self.backend / "current").exists())
        self.assertEqual(list((self.backend / "releases").iterdir()), [])
        self.assertEqual(len(list((self.base / "production/backups").glob("*.sql.gz"))), 1)

    def test_failed_secret_write_removes_the_incomplete_file(self):
        target = self.backend / ".env"

        def failing_writer(descriptor, mode):
            handle = MagicMock()
            handle.__enter__.return_value.write.side_effect = OSError(PRIVATE_VALUE)
            handle.__exit__.side_effect = lambda *args: os.close(descriptor)
            return handle

        with patch.object(ADOPT.os, "fdopen", side_effect=failing_writer), self.assertRaises(OSError):
            ADOPT.private_file(target, PRIVATE_VALUE)
        self.assertFalse(target.exists())

    @unittest.skipUnless(shutil.which("docker"), "Docker Compose CLI is required")
    def test_real_compose_preserves_literal_credentials_and_adopted_volume(self):
        services = ADOPT.services_from(self.containers)
        (self.base / "snapshot.json").write_text(ADOPT.snapshot(services))
        (self.base / "test.env").write_text(ADOPT.dotenv(ADOPT.credentials(services, TEST_ORIGIN)))
        for source, extra in ((self.base / "snapshot.json", []),
                              (ROOT / "deploy/digitalocean/compose.yaml", ["--env-file", str(self.base / "test.env")])):
            result = subprocess.run(["docker", "compose", "--project-name", "quizproject", "--env-file", "/dev/null",
                                     *extra, "--file", str(source), "config", "--format", "json"],
                                    env={**os.environ, "APP_IMAGE": services["api"]["Config"]["Image"], "DEPLOY_ENV": "production"},
                                    capture_output=True, text=True, timeout=20)
            self.assertEqual(result.returncode, 0, result.stderr)
            compose = json.loads(result.stdout)
            # Compose escapes dollars again when serializing a reusable config.
            self.assertEqual(compose["services"]["api"]["environment"]["DB_PASSWORD"], PRIVATE_VALUE.replace("$", "$$"))
            mount = next(item for item in compose["services"]["mysql"]["volumes"] if item["target"] == "/var/lib/mysql")
            self.assertEqual(compose["volumes"][mount["source"]], {"name": "quizproject_mysql_data", "external": True})
    def test_mismatched_credentials_and_volumes_are_rejected(self):
        for url in ("jdbc:mysql://foreign:3306/quizproject", "jdbc:postgresql://mysql:3306/quizproject"):
            services = ADOPT.services_from(copy.deepcopy(self.containers))
            env = ADOPT.environment(services["api"])
            env["DB_URL"] = url
            services["api"]["Config"]["Env"] = [name + "=" + value for name, value in env.items()]
            with self.assertRaises(ValueError):
                ADOPT.credentials(services, TEST_ORIGIN)
        for service, key in (("api", "DB_URL"), ("api", "DB_USERNAME"), ("api", "DB_PASSWORD"), ("api", "REDIS_PASSWORD")):
            services = ADOPT.services_from(copy.deepcopy(self.containers))
            env = ADOPT.environment(services[service])
            env[key] = "different"
            services[service]["Config"]["Env"] = [name + "=" + value for name, value in env.items()]
            with self.subTest(key=key), self.assertRaises(ValueError):
                ADOPT.credentials(services, TEST_ORIGIN)
        for mounts in ([], [{"Destination": "/var/lib/mysql", "Name": "foreign"}]):
            services = ADOPT.services_from(copy.deepcopy(self.containers))
            services["mysql"]["Mounts"] = mounts
            with self.assertRaises(ValueError):
                ADOPT.credentials(services, TEST_ORIGIN)

    def test_unexpected_stack_state_is_rejected(self):
        mutations = (
            lambda c: c[0]["Config"]["Labels"].update({"com.docker.compose.project": "foreign"}),
            lambda c: c[0]["Config"]["Labels"].update({"com.docker.compose.service": "unknown"}),
            lambda c: c.append(copy.deepcopy(c[0])),
            lambda c: c[0]["State"].update(Running=False),
            lambda c: c[0]["Config"].update(Image="mysql:latest"),
            lambda c: c.pop(),
        )
        for mutate in mutations:
            containers = copy.deepcopy(self.containers)
            mutate(containers)
            with self.assertRaises(ValueError):
                ADOPT.services_from(containers)

    def test_snapshot_preserves_security_and_supports_optional_metadata(self):
        services = ADOPT.services_from(self.containers)
        result = json.loads(ADOPT.snapshot(services))
        api = result["services"]["api"]
        self.assertEqual(api["cap_drop"], ["ALL"])
        self.assertTrue(api["read_only"])
        self.assertEqual(api["security_opt"], ["no-new-privileges:true"])
        self.assertEqual(api["tmpfs"], ["/run/quiz-tmp:rw"])
        self.assertEqual(api["stop_grace_period"], "30s")
        self.assertEqual(result["services"]["mysql"]["healthcheck"]["interval"], "10000000000ns")
        container = copy.deepcopy(self.containers[0])
        container["Config"].update(User="", WorkingDir="", Entrypoint=None, Cmd=None)
        container["Config"].pop("StopTimeout")
        container["HostConfig"].update(PidsLimit=None, Tmpfs={}, LogConfig={}, RestartPolicy={"Name": ""})
        container["NetworkSettings"] = {"Networks": {"test": {"Aliases": None}}, "Ports": None}
        container["Mounts"] = [{"Type": "bind", "Source": "/host/path", "Destination": "/container/path", "RW": False}]
        metadata = ADOPT.snapshot_service(container, {}, {})
        self.assertEqual(metadata["restart"], "no")
        self.assertEqual(metadata["volumes"][0]["type"], "bind")
        self.assertTrue(metadata["volumes"][0]["read_only"])
        self.assertEqual(metadata["healthcheck"], {})
        container["HostConfig"]["Tmpfs"] = {"/run/quiz-empty": ""}
        self.assertEqual(ADOPT.snapshot_service(container, {}, {})["tmpfs"], ["/run/quiz-empty"])
        self.assertEqual(ADOPT.snapshot_health({"Test": ["NONE"]}), {"test": ["NONE"]})
        container["HostConfig"]["Privileged"] = True
        with self.assertRaises(ValueError):
            ADOPT.snapshot_service(container, {}, {})
        with self.assertRaises(ValueError):
            ADOPT.snapshot_mounts({"Mounts": [{"Type": "unsupported"}]}, {})

    def test_origins_and_dotenv_literals_are_validated(self):
        self.assertEqual(ADOPT.addresses("https://quiz.example.com/"), ("https://quiz.example.com", "quiz.example.com", 80))
        origin, listener, port = ADOPT.addresses("http://127.0.0.1:18080")
        self.assertEqual(origin, "http://127.0.0.1:18080")
        self.assertEqual((urlsplit(listener).scheme, urlsplit(listener).port, port), ("http", 80, 18080))
        self.assertEqual(ADOPT.addresses("https://quiz.example.com:443")[0], "https://quiz.example.com")
        for origin in ("https://user:pass@host", "https://host/path", "https://host:8443", "http://localhost:443", "http://localhost:0", "http://localhost:65536"):
            with self.assertRaises(ValueError):
                ADOPT.addresses(origin)
        self.assertEqual(ADOPT.dotenv({"VALUE": "$literal's"}), "VALUE='$literal\\'s'\n")
        for value in ("", "line\nline", "\r", "\0", "trailing\\"):
            with self.assertRaises(ValueError):
                ADOPT.dotenv({"VALUE": value})

    def test_docker_is_local_and_errors_do_not_disclose_credentials(self):
        result = subprocess.CompletedProcess([], 0, b"result", b"")
        with patch.dict(os.environ, {"DOCKER_HOST": "tcp://foreign:2375", "DOCKER_CONTEXT": "foreign"}), \
                patch.object(ADOPT.subprocess, "run", return_value=result) as run:
            self.assertEqual(ADOPT.docker(["inspect"]), b"result")
        args, kwargs = run.call_args
        self.assertIn("--host=unix:///var/run/docker.sock", args[0])
        self.assertNotIn("DOCKER_CONTEXT", kwargs["env"])
        with patch.object(ADOPT.subprocess, "run", return_value=subprocess.CompletedProcess([], 1, PRIVATE_VALUE, PRIVATE_VALUE)), \
                self.assertRaisesRegex(RuntimeError, "^Docker operation failed"):
            ADOPT.docker(["inspect"])

    def test_main_uses_the_host_lock_and_redacts_failures(self):
        with patch.object(ADOPT, "docker", side_effect=self.docker), \
                patch("sys.argv", [str(SOURCE), "--root", str(self.base), "--origin", TEST_ORIGIN]), contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(ADOPT.main(), 0)
        errors = io.StringIO()
        with patch.object(ADOPT, "prepare", side_effect=ValueError(PRIVATE_VALUE)), \
                patch("sys.argv", [str(SOURCE), "--root", str(self.base), "--origin", TEST_ORIGIN]), contextlib.redirect_stderr(errors):
            self.assertEqual(ADOPT.main(), 1)
        self.assertNotIn(PRIVATE_VALUE, errors.getvalue())

    def test_cli_entrypoint_has_sanitized_failure(self):
        errors = io.StringIO()
        with patch("sys.argv", [str(SOURCE), "--root", str(self.base / "missing"), "--origin", TEST_ORIGIN]), \
                contextlib.redirect_stderr(errors), self.assertRaises(SystemExit) as exited:
            runpy.run_path(str(SOURCE), run_name="__main__")
        self.assertEqual(exited.exception.code, 1)
        self.assertNotIn("Traceback", errors.getvalue())


if __name__ == "__main__":
    unittest.main()
