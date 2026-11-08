"""Protect inspection logs from credentials in Docker metadata and errors."""
import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import runpy
import subprocess
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "deploy/digitalocean/preflight.py"
SPEC = importlib.util.spec_from_file_location("digitalocean_preflight", SOURCE)
PREFLIGHT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(PREFLIGHT)
SECRET = "credential-value-must-never-appear"


class PreflightTest(unittest.TestCase):
    def test_only_allowed_metadata_reaches_output(self):
        container = {
            "Name": "/quizproject-api-1",
            "Config": {
                "Image": "ghcr.io/vitaliilatysh/quizproject@sha256:" + "a" * 64,
                "Env": ["JWT_SECRET=" + SECRET, "DB_PASSWORD=" + SECRET],
                "Cmd": ["redis-server", "--requirepass", SECRET],
                "Labels": {"com.docker.compose.project": "quizproject", "other": SECRET},
            },
            "State": {"Status": "running", "Health": {"Status": "healthy", "Log": [SECRET]}},
            "NetworkSettings": {"Networks": {"quizproject_default": {"Other": SECRET}},
                                "Ports": {"8081/tcp": [{"HostIp": "127.0.0.1", "HostPort": "8081"}]}},
            "Mounts": [{"Type": "volume", "Name": "quizproject_mysql_data", "Destination": "/var/lib/mysql",
                        "Source": "/var/lib/docker/volumes/quizproject_mysql_data/_data", "RW": True,
                        "Extra": SECRET}],
            "Other": SECRET,
        }
        with patch.object(PREFLIGHT, "docker_output", side_effect=["abc\n", json.dumps([container])]), \
                patch.object(PREFLIGHT, "ENVIRONMENT_FILES", ()):
            output, errors = io.StringIO(), io.StringIO()
            with contextlib.redirect_stdout(output), contextlib.redirect_stderr(errors):
                self.assertEqual(PREFLIGHT.main(), 0)
        report = json.loads(output.getvalue())
        self.assertNotIn(SECRET, output.getvalue() + errors.getvalue())
        metadata = report["containers"][0]
        self.assertEqual(metadata["environment_names"], ["DB_PASSWORD", "JWT_SECRET"])
        self.assertEqual(metadata["compose"]["com.docker.compose.project"], "quizproject")
        self.assertEqual(metadata["networks"], ["quizproject_default"])
        self.assertEqual(metadata["health"], "healthy")
        self.assertEqual(metadata["mounts"][0]["Name"], "quizproject_mysql_data")

    def test_missing_optional_metadata_and_empty_hosts_are_supported(self):
        metadata = PREFLIGHT.container_metadata({"Config": {"Env": None}, "Mounts": None})
        self.assertEqual(metadata["environment_names"], [])
        self.assertEqual(metadata["mounts"], [])
        self.assertIsNone(metadata["health"])
        with patch.object(PREFLIGHT, "docker_output", return_value="") as docker, \
                patch.object(PREFLIGHT, "ENVIRONMENT_FILES", ()):
            self.assertEqual(PREFLIGHT.collect()["containers"], [])
        self.assertEqual(docker.call_count, 1)

    def test_file_checks_read_only_permissions_never_contents(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / ".env"
            path.write_text("JWT_SECRET=" + SECRET)
            path.chmod(0o600)
            metadata = PREFLIGHT.file_metadata(path)
            self.assertEqual(metadata["mode"], "0600")
            self.assertTrue(metadata["readable"])
            self.assertEqual(metadata["owner_uid"], os.getuid())
            self.assertNotIn(SECRET, json.dumps(metadata))
            with patch.object(PREFLIGHT, "docker_output", return_value=""), \
                    patch.object(PREFLIGHT, "ENVIRONMENT_FILES", (str(path),)):
                self.assertEqual(PREFLIGHT.collect()["environment_files"], [metadata])
            with patch.object(Path, "stat", side_effect=PermissionError(SECRET)):
                self.assertEqual(PREFLIGHT.file_metadata(path),
                                 {"path": str(path), "readable": False, "mode": None, "owner_uid": None})

    def test_local_daemon_is_pinned_and_docker_errors_are_redacted(self):
        with patch.dict(os.environ, {"DOCKER_HOST": "tcp://foreign:2375", "DOCKER_CONTEXT": "foreign"}), \
                patch.object(PREFLIGHT.subprocess, "run", return_value=subprocess.CompletedProcess([], 0, "result", "")) as run:
            self.assertEqual(PREFLIGHT.docker_output("ps"), "result")
            args, kwargs = run.call_args
            self.assertEqual(args[0], ["docker", "--host=unix:///var/run/docker.sock", "ps"])
            self.assertNotIn("DOCKER_HOST", kwargs["env"])
            self.assertNotIn("DOCKER_CONTEXT", kwargs["env"])
        with patch.object(PREFLIGHT.subprocess, "run", return_value=subprocess.CompletedProcess([], 1, SECRET, SECRET)):
            with self.assertRaisesRegex(RuntimeError, "^Docker inspection failed\\.$"):
                PREFLIGHT.docker_output("inspect", "abc")

    def test_errors_cannot_expose_docker_output_credentials_or_tracebacks(self):
        failures = (OSError(SECRET), ValueError(SECRET), RuntimeError(SECRET), TypeError(SECRET),
                    subprocess.TimeoutExpired([SECRET], 30, output=SECRET, stderr=SECRET))
        for error in failures:
            with self.subTest(error=type(error).__name__), patch.object(PREFLIGHT, "collect", side_effect=error):
                output, errors = io.StringIO(), io.StringIO()
                with contextlib.redirect_stdout(output), contextlib.redirect_stderr(errors):
                    self.assertEqual(PREFLIGHT.main(), 1)
                self.assertEqual(output.getvalue(), "")
                self.assertNotIn(SECRET, errors.getvalue())
                self.assertNotIn("Traceback", errors.getvalue())

    def test_unexpected_docker_format_is_rejected(self):
        with patch.object(PREFLIGHT, "docker_output", side_effect=["abc", "{}"]):
            with self.assertRaisesRegex(ValueError, "Unexpected Docker inspection format"):
                PREFLIGHT.collect()

    def test_cli_entrypoint_redacts_subprocess_failures(self):
        with tempfile.TemporaryDirectory() as directory:
            docker = Path(directory) / "docker"
            docker.write_text("#!/bin/sh\nprintf '%s' '" + SECRET + "' >&2\nexit 1\n")
            docker.chmod(0o700)
            result = subprocess.run([os.sys.executable, str(SOURCE)], text=True, capture_output=True,
                                    env={**os.environ, "PATH": directory}, timeout=10)
            self.assertEqual(result.returncode, 1)
            self.assertNotIn(SECRET, result.stdout + result.stderr)
            self.assertNotIn("Traceback", result.stderr)

    def test_cli_entrypoint_reports_an_empty_stack_successfully(self):
        output = io.StringIO()
        with patch("subprocess.run", return_value=subprocess.CompletedProcess([], 0, "", "")), \
                contextlib.redirect_stdout(output), self.assertRaises(SystemExit) as exited:
            runpy.run_path(str(SOURCE), run_name="__main__")
        self.assertEqual(exited.exception.code, 0)
        self.assertEqual(json.loads(output.getvalue())["containers"], [])


if __name__ == "__main__":
    unittest.main()
