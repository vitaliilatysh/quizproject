#!/usr/bin/env bash
set -euo pipefail
[[ "${CI:-}" == true && "${GITHUB_RUN_ID:-}" =~ ^[0-9]+$ ]]
root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
temporary=$(mktemp -d)
export DEPLOY_ENV="ci-${GITHUB_RUN_ID}-${GITHUB_RUN_ATTEMPT:-1}"
project="quizproject-$DEPLOY_ENV-backend"
compose() {
  docker compose --project-name "$project" --env-file "$temporary/.env" \
    --file "$root/deploy/digitalocean/compose.yaml" "$@"
}
cleanup() {
  compose down --volumes --remove-orphans >/dev/null 2>&1 || true

  rm -rf -- "$temporary"
}
trap cleanup EXIT

umask 077
python3 - "$temporary/.env" <<'PY'
import base64,secrets,sys
from pathlib import Path
Path(sys.argv[1]).write_text("PUBLIC_ORIGIN=http://localhost\nMYSQL_PASSWORD="+secrets.token_hex(32)+
    "\nMYSQL_ROOT_PASSWORD="+secrets.token_hex(32)+"\nREDIS_PASSWORD="+secrets.token_hex(32)+
    "\nJWT_SECRET="+base64.b64encode(secrets.token_bytes(32)).decode()+"\n")
PY
export APP_IMAGE="${TEST_IMAGE:?The locally built backend image is required}"
compose up --detach --wait --wait-timeout 180
compose exec -T api bash -ec \
  "exec 3<>/dev/tcp/127.0.0.1/8081; printf 'GET /api/v1/quizzes HTTP/1.0\r\nHost: localhost\r\n\r\n' >&3; head -n 1 <&3 | grep -q ' 200 '"
echo 'DigitalOcean backend Compose readiness and public API passed.'
