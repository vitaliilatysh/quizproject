#!/usr/bin/env python3
"""Back up and prepare the existing quizproject stack without restarting it."""
import argparse
from contextlib import ExitStack
import fcntl
import gzip
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
from urllib.parse import urlsplit


SERVICES = ("api", "mysql", "redis")
CONTAINERS = [f"quizproject-{service}-1" for service in SERVICES]
DOCKER_VARIABLES = {"DOCKER_HOST", "DOCKER_CONTEXT", "DOCKER_TLS", "DOCKER_TLS_VERIFY", "DOCKER_CERT_PATH"}
BOOTSTRAP_ORIGIN = "http://164.92.163.71"  # NOSONAR(S5332): connectivity bootstrap before a domain is chosen.
BOOTSTRAP_SITE = "http://:80"  # NOSONAR(S5332): explicit HTTP bootstrap listener.


def docker(arguments, output=None):
    result = subprocess.run(
        ["docker", "--host=unix:///var/run/docker.sock", *arguments],
        stdout=subprocess.PIPE if output is None else output, stderr=subprocess.PIPE,
        timeout=240, check=False,
        env={name: value for name, value in os.environ.items() if name not in DOCKER_VARIABLES},
    )
    if result.returncode:
        raise RuntimeError("Docker operation failed; details must be inspected privately.")
    return result.stdout or b""


def private_file(path, content):
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    try:
        with os.fdopen(descriptor, "w") as handle:
            handle.write(content)
    except OSError:
        path.unlink(missing_ok=True)
        raise


def environment(container):
    return dict(entry.split("=", 1) for entry in container["Config"]["Env"])


def addresses(origin):
    match = re.fullmatch(r"(https?)://([a-zA-Z0-9][a-zA-Z0-9.-]*)(?::([0-9]+))?/?", origin)
    if not match:
        raise ValueError("An HTTP(S) origin without credentials or a path is required.")
    scheme, host, explicit_port = match.groups()
    port = int(explicit_port) if explicit_port else (443 if scheme == "https" else 80)
    if not 1 <= port <= 65535 or (scheme == "https" and port != 443) or (scheme == "http" and port == 443):
        raise ValueError("Unsupported public port.")
    origin = f"{scheme}://{host}"
    if port not in (80, 443):
        origin += f":{port}"
    return origin, host if scheme == "https" else BOOTSTRAP_SITE, 80 if scheme == "https" else port


def dotenv(values):
    lines = []
    for name, value in values.items():
        if not value or any(character in value for character in ("\n", "\r", "\0")) or value.endswith("\\"):
            raise ValueError("Missing or unsupported environment value.")
        quoted = value.replace("'", "\\'")
        lines.append(f"{name}='{quoted}'\n")
    return "".join(lines)


def services_from(containers):
    services = {}
    for container in containers:
        labels = container["Config"]["Labels"]
        name = labels.get("com.docker.compose.service")
        if (labels.get("com.docker.compose.project") != "quizproject" or name not in SERVICES
                or name in services or not container["State"]["Running"]
                or not re.fullmatch(r".+@sha256:[0-9a-f]{64}", container["Config"]["Image"])):
            raise ValueError("The running stack does not match the expected deployment.")
        services[name] = container
    if set(services) != set(SERVICES):
        raise ValueError("All three existing services are required.")
    return services


def check_database_target(services, api, mysql):
    database = urlsplit(api["DB_URL"].removeprefix("jdbc:"))
    hosts = {"mysql", "quizproject-mysql-1"}
    hosts.update(alias for settings in services["mysql"]["NetworkSettings"]["Networks"].values()
                 for alias in settings.get("Aliases") or [])
    if (database.scheme != "mysql" or database.hostname not in hosts
            or database.path.lstrip("/") != mysql["MYSQL_DATABASE"]):
        raise ValueError("The backend must use the existing MySQL database.")


def credentials(services, origin):
    api, mysql, redis = (environment(services[name]) for name in SERVICES)
    check_database_target(services, api, mysql)
    if (api["DB_USERNAME"] != mysql["MYSQL_USER"] or api["DB_PASSWORD"] != mysql["MYSQL_PASSWORD"]
            or api["REDIS_PASSWORD"] != redis["REDIS_PASSWORD"]):
        raise ValueError("Existing service credentials do not agree.")
    for name, destination in (("mysql", "/var/lib/mysql"), ("redis", "/data")):
        mounts = [mount for mount in services[name]["Mounts"] if mount["Destination"] == destination]
        if len(mounts) != 1 or mounts[0].get("Name") != f"quizproject_{name}_data":
            raise ValueError("The expected persistent volume is missing.")
    return {
        "PUBLIC_ORIGIN": origin,
        **{name: mysql[name] for name in ("MYSQL_DATABASE", "MYSQL_USER", "MYSQL_PASSWORD", "MYSQL_ROOT_PASSWORD")},
        "JWT_SECRET": api["JWT_SECRET"], "REDIS_PASSWORD": redis["REDIS_PASSWORD"],
        "MYSQL_IMAGE": services["mysql"]["Config"]["Image"],
        "REDIS_IMAGE": services["redis"]["Config"]["Image"],
        "MYSQL_DATA_VOLUME": "quizproject_mysql_data", "MYSQL_DATA_EXTERNAL": "true",
    }


def literal(value):
    if isinstance(value, str):
        return value.replace("$", "$$")
    if isinstance(value, list):
        return [literal(item) for item in value]
    if isinstance(value, dict):
        return {name: literal(item) for name, item in value.items()}
    return value


def snapshot_mounts(container, volumes):
    mounts = []
    for mount in container["Mounts"]:
        kind = mount["Type"]
        if kind not in ("bind", "volume"):
            raise ValueError("Unsupported mount type in existing stack.")
        source = mount["Source"]
        if kind == "volume":
            source = mount["Name"]
            volumes[source] = {"name": source, "external": True}
        mounts.append({"type": kind, "source": source, "target": mount["Destination"],
                       "read_only": not mount["RW"]})
    return mounts


def snapshot_ports(network_settings):
    ports = []
    for endpoint, bindings in (network_settings.get("Ports") or {}).items():
        target, protocol = endpoint.split("/")
        for binding in bindings or []:
            ports.append({"target": int(target), "protocol": protocol,
                          "published": binding["HostPort"], "host_ip": binding["HostIp"]})
    return ports


def snapshot_health(health):
    result = {}
    if health:
        result = {"test": health["Test"]}
        for field, source in (("interval", "Interval"), ("timeout", "Timeout"),
                              ("start_period", "StartPeriod"), ("start_interval", "StartInterval")):
            if health.get(source):
                result[field] = f"{health[source]}ns"
        if "Retries" in health:
            result["retries"] = health["Retries"]
    return result


def snapshot_service(container, volumes, networks):
    config, host = container["Config"], container["HostConfig"]
    if host.get("Privileged"):
        raise ValueError("Privileged containers cannot be adopted.")
    for name in container["NetworkSettings"]["Networks"]:
        networks[name] = {"name": name, "external": True}
    service = {
        "image": config["Image"], "pull_policy": "never", "environment": environment(container),
        "entrypoint": config.get("Entrypoint") or [], "command": config.get("Cmd") or [],
        "restart": host["RestartPolicy"]["Name"] or "no",
        "read_only": host["ReadonlyRootfs"], "init": bool(host.get("Init")),
        "cap_drop": host.get("CapDrop") or [], "cap_add": host.get("CapAdd") or [],
        "security_opt": host.get("SecurityOpt") or [], "mem_limit": host["Memory"],
        "networks": {name: {"aliases": settings.get("Aliases") or []}
                     for name, settings in container["NetworkSettings"]["Networks"].items()},
        "volumes": snapshot_mounts(container, volumes),
        "ports": snapshot_ports(container["NetworkSettings"]),
        "healthcheck": snapshot_health(config.get("Healthcheck")),
    }
    for field, source in (("user", "User"), ("working_dir", "WorkingDir")):
        if config.get(source):
            service[field] = config[source]
    if host.get("PidsLimit") is not None:
        service["pids_limit"] = host["PidsLimit"]
    if config.get("StopTimeout") is not None:
        service["stop_grace_period"] = f"{config['StopTimeout']}s"
    if host.get("Tmpfs"):
        service["tmpfs"] = [f"{path}:{options}" for path, options in host["Tmpfs"].items()]
    if host.get("LogConfig", {}).get("Type"):
        service["logging"] = {"driver": host["LogConfig"]["Type"], "options": host["LogConfig"].get("Config") or {}}
    return service


def snapshot(services):
    volumes, networks = {}, {}
    result = {"services": {name: snapshot_service(container, volumes, networks)
                           for name, container in services.items()}, "volumes": volumes, "networks": networks}
    result["services"]["api"]["depends_on"] = {
        name: {"condition": "service_healthy"} for name in ("mysql", "redis")
    }
    return json.dumps(literal(result), indent=2)


def backup(base):
    directory = base / "production/backups"
    directory.mkdir(mode=0o700, exist_ok=True)
    descriptor, filename = tempfile.mkstemp(prefix="mysql-before-adoption-", suffix=".sql", dir=directory)
    raw = Path(filename)
    try:
        with os.fdopen(descriptor, "wb") as handle:
            docker(["exec", "quizproject-mysql-1", "sh", "-ec",
                    'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysqldump --user=root '
                    '--single-transaction --quick --routines --events --triggers --no-tablespaces '
                    '--set-gtid-purged=OFF --databases "$MYSQL_DATABASE"'], output=handle)
        with raw.open("rb") as handle:
            handle.seek(max(0, raw.stat().st_size - 512))
            if b"-- Dump completed on" not in handle.read():
                raise RuntimeError("The database dump is incomplete.")
        compressed = raw.with_suffix(".sql.gz")
        with ExitStack() as cleanup:
            compressed_descriptor = os.open(compressed, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            cleanup.callback(compressed.unlink, missing_ok=True)
            with os.fdopen(compressed_descriptor, "wb") as destination, raw.open("rb") as source, \
                    gzip.GzipFile(fileobj=destination, mode="wb") as archive:
                shutil.copyfileobj(source, archive)
            cleanup.pop_all()
        return compressed
    finally:
        raw.unlink(missing_ok=True)


def install_state(backend, frontend, staged, release, backend_text, frontend_text, saved):
    with ExitStack() as cleanup:
        release.mkdir(mode=0o700)
        cleanup.callback(shutil.rmtree, release)
        shutil.copytree(staged, release, dirs_exist_ok=True)
        for path, content in ((backend / ".env", backend_text), (frontend / ".env", frontend_text),
                              (backend / ".compose-project", "quizproject\n")):
            private_file(path, content)
            cleanup.callback(path.unlink, missing_ok=True)
        (backend / "current").symlink_to(release)
        cleanup.callback((backend / "current").unlink, missing_ok=True)
        private_file(backend / "adopted.json", json.dumps({
            "project": "quizproject", "mysql_volume": "quizproject_mysql_data",
            "backup": str(saved), "rollback": str(release),
        }, indent=2))
        cleanup.pop_all()


def prepare(base, origin):
    origin, site, port = addresses(origin)
    backend, frontend = base / "production/backend", base / "production/frontend"
    marker = backend / "adopted.json"
    required = (backend / ".env", frontend / ".env", backend / ".compose-project", backend / "current")
    if marker.exists():
        if not all(path.exists() for path in required):
            raise ValueError("The existing preparation is incomplete.")
        print("Existing deployment is already prepared; credentials were preserved.")
        return
    if any(path.exists() or path.is_symlink() for path in required):
        raise FileExistsError("Existing deployment files cannot be overwritten.")
    services = services_from(json.loads(docker(["inspect", *CONTAINERS])))
    backend_text = dotenv(credentials(services, origin))
    frontend_text = dotenv({"SITE_ADDRESS": site, "HTTP_PORT": str(port), "HTTPS_PORT": "443"})
    rollback_text = snapshot(services)
    release = backend / "releases/0-0"
    if release.exists():
        raise FileExistsError("The initial rollback release already exists.")
    with tempfile.TemporaryDirectory(prefix=".adopt-", dir=backend / "releases") as temporary:
        staged = Path(temporary)
        private_file(staged / "compose.yaml", rollback_text)
        private_file(staged / "image.env", f"APP_IMAGE={services['api']['Config']['Image']}\nDEPLOY_ENV=production\n")
        docker(["compose", "--project-name", "quizproject", "--env-file", "/dev/null",
                "--file", str(staged / "compose.yaml"), "config", "--quiet"])
        print("Creating a private MySQL backup; existing services continue running.")
        saved = backup(base)
        install_state(backend, frontend, staged, release, backend_text, frontend_text, saved)
    print("Prepared the existing database, pinned infrastructure images and initial rollback release.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path("/opt/quizproject"))
    parser.add_argument("--origin", default=BOOTSTRAP_ORIGIN)
    args = parser.parse_args()
    try:
        lock_path = args.root / "production/.deploy.lock"
        descriptor = os.open(lock_path, os.O_RDWR | os.O_CREAT | os.O_NOFOLLOW, 0o600)
        with os.fdopen(descriptor, "a") as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            prepare(args.root, args.origin)
    except (OSError, ValueError, KeyError, RuntimeError, TypeError, subprocess.SubprocessError):
        print("Preparation failed; inspect permissions and deployment state privately. Containers were not restarted.",
              file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
