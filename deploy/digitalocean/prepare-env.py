#!/usr/bin/env python3
"""Create server-only credentials for a new environment; never replace secrets."""
import argparse
import base64
import os
from pathlib import Path
import re
import secrets
from urllib.parse import urlsplit


def prepare(base, environment, origin):
    parsed = urlsplit(origin)
    if (parsed.scheme not in ("http", "https") or not parsed.hostname
            or not re.fullmatch(r"[a-zA-Z0-9][a-zA-Z0-9.-]*", parsed.hostname)
            or parsed.username or parsed.password or parsed.query or parsed.fragment
            or parsed.path not in ("", "/")):
        raise ValueError("Use an HTTP(S) origin without credentials, a path or a query.")
    port = parsed.port or (443 if parsed.scheme == "https" else 80)
    if parsed.scheme == "https" and port != 443:
        raise ValueError("HTTPS requires public port 443 for automatic certificates.")
    if parsed.scheme == "http" and port == 443:
        raise ValueError("Port 443 is reserved for HTTPS.")
    origin = f"{parsed.scheme}://{parsed.hostname}" + (f":{port}" if port != (443 if parsed.scheme == "https" else 80) else "")
    backend = base / environment / "backend"
    frontend = base / environment / "frontend"
    paths = (backend / ".env", frontend / ".env")
    if any(path.exists() or path.is_symlink() for path in paths):
        raise FileExistsError("Environment already exists. Existing credentials were not changed.")
    backend_text = (f"PUBLIC_ORIGIN={origin}\nMYSQL_DATABASE=quizproject\nMYSQL_USER=quiz_app\n"
                    f"MYSQL_PASSWORD={secrets.token_hex(32)}\n"
                    f"MYSQL_ROOT_PASSWORD={secrets.token_hex(32)}\n"
                    f"JWT_SECRET={base64.b64encode(secrets.token_bytes(32)).decode()}\n"
                    f"REDIS_PASSWORD={secrets.token_hex(32)}\n")
    site = parsed.hostname if parsed.scheme == "https" else "http://:80"
    frontend_text = f"SITE_ADDRESS={site}\nHTTP_PORT={port if parsed.scheme=='http' else 80}\nHTTPS_PORT=443\n"
    for directory in (backend, frontend):
        (directory / "releases").mkdir(parents=True, mode=0o700, exist_ok=True)
        directory.chmod(0o700)
    for path,text in zip(paths,(backend_text,frontend_text)):
        fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd,"w") as handle:
            handle.write(text)
    print("Created backend and frontend environment files; credential values are not printed.")


if __name__ == "__main__":
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root",type=Path,default=Path("/opt/quizproject"))
    parser.add_argument("--environment",choices=("staging","production"),default="production")
    parser.add_argument("--origin",required=True)
    args=parser.parse_args()
    prepare(args.root,args.environment,args.origin)
