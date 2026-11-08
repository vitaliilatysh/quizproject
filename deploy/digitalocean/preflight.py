#!/usr/bin/env python3
"""Read the existing Droplet stack without logging credentials or changing it."""
import json
import os
from pathlib import Path
import stat
import subprocess
import sys


COMPOSE_LABELS = (
    "com.docker.compose.project",
    "com.docker.compose.service",
    "com.docker.compose.project.working_dir",
    "com.docker.compose.project.config_files",
)
ENVIRONMENT_FILES = (
    "/opt/quizproject/.env",
    "/opt/quizproject/production/backend/.env",
    "/opt/quizproject/production/frontend/.env",
)
DOCKER_CONNECTION_VARIABLES = {
    "DOCKER_HOST", "DOCKER_CONTEXT", "DOCKER_TLS", "DOCKER_TLS_VERIFY", "DOCKER_CERT_PATH",
}


def docker_output(*arguments):
    result = subprocess.run(
        ["docker", "--host=unix:///var/run/docker.sock", *arguments],
        capture_output=True, text=True, timeout=30, check=False,
        env={key: value for key, value in os.environ.items()
             if key not in DOCKER_CONNECTION_VARIABLES},
    )
    if result.returncode:
        # Docker errors can contain resolved environment values; do not log them.
        raise RuntimeError("Docker inspection failed.")
    return result.stdout


def container_metadata(container):
    config = container.get("Config") or {}
    state = container.get("State") or {}
    network = container.get("NetworkSettings") or {}
    labels = config.get("Labels") or {}
    return {
        "name": container.get("Name"),
        "image": config.get("Image"),
        "state": state.get("Status"),
        "health": (state.get("Health") or {}).get("Status"),
        "compose": {name: labels.get(name) for name in COMPOSE_LABELS},
        "environment_names": sorted(entry.split("=", 1)[0]
                                    for entry in config.get("Env") or []),
        "networks": sorted((network.get("Networks") or {}).keys()),
        "ports": network.get("Ports") or {},
        "mounts": [
            {name: mount.get(name) for name in ("Type", "Name", "Source", "Destination", "RW")}
            for mount in container.get("Mounts") or []
        ],
    }


def file_metadata(path):
    try:
        info = path.stat()
    except OSError:
        return {"path": str(path), "readable": False, "mode": None, "owner_uid": None}
    return {"path": str(path), "readable": os.access(path, os.R_OK),
            "mode": format(stat.S_IMODE(info.st_mode), "04o"), "owner_uid": info.st_uid}


def collect():
    identifiers = docker_output(
        "ps", "--all", "--filter", "label=com.docker.compose.project", "--format", "{{.ID}}",
    ).split()
    containers = json.loads(docker_output("inspect", *identifiers)) if identifiers else []
    if not isinstance(containers, list):
        raise ValueError("Unexpected Docker inspection format.")
    return {
        "uid": os.getuid(),
        "containers": [container_metadata(container) for container in containers],
        "environment_files": [file_metadata(Path(path)) for path in ENVIRONMENT_FILES],
    }


def main():
    try:
        report = collect()
    except (OSError, ValueError, RuntimeError, TypeError, subprocess.SubprocessError):
        print("Cannot inspect the Droplet stack; check SSH, Docker access and file permissions.",
              file=sys.stderr)
        return 1
    print(json.dumps(report, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
