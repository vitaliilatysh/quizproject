#!/usr/bin/env bash
# Runs on the droplet, from /opt/quizproject, over SSH from the deploy workflow:
#
#   ./deploy.sh ghcr.io/vitaliilatysh/quizproject@sha256:... \
#               ghcr.io/vitaliilatysh/quizproject-web@sha256:...
#
# Starts the stack on the two images, waits for every healthcheck, then probes the
# API and the frontend through the compose network. If anything fails, the
# previous images are started again and the script exits non-zero, so a bad
# release leaves the droplet serving the last good one.
set -euo pipefail

if [ "$#" -ne 2 ]; then
  echo "usage: $0 <api-image-ref> <web-image-ref>" >&2
  exit 64
fi

API_IMAGE="$1"
WEB_IMAGE="$2"

cd "$(dirname "$0")"

if [ ! -f .env ]; then
  echo "Missing .env: copy env.example to .env and fill it in first." >&2
  exit 1
fi

# Only a digest reference is accepted. A moving tag would make "what is running"
# depend on when somebody last pushed, which is what the signature check on the
# runner exists to prevent.
for ref in "$API_IMAGE" "$WEB_IMAGE"; do
  case "$ref" in
    ghcr.io/vitaliilatysh/*@sha256:????????????????????????????????????????????????????????????????) ;;
    *) echo "Refusing image reference that is not a ghcr.io digest: $ref" >&2; exit 64 ;;
  esac
done

compose() { docker compose --env-file .env --env-file images.env "$@"; }

if [ -f images.env ]; then
  cp images.env images.previous.env
fi
printf 'API_IMAGE=%s\nWEB_IMAGE=%s\n' "$API_IMAGE" "$WEB_IMAGE" > images.env

smoke_test() {
  compose exec -T caddy wget --quiet --output-document=/dev/null http://web:8080/healthz \
    && compose exec -T caddy wget --quiet --output-document=/dev/null http://api:8081/api/v1/quizzes/summary
}

rollback() {
  echo "Deploy failed; showing recent logs." >&2
  compose logs --tail 60 api web caddy >&2 || true
  if [ -f images.previous.env ]; then
    echo "Rolling back to the previous images." >&2
    cp images.previous.env images.env
    compose up --detach --remove-orphans --wait --wait-timeout 180 \
      || echo "Rollback did not come up cleanly either; check the droplet." >&2
  else
    echo "No previous release to roll back to." >&2
  fi
  exit 1
}

compose pull api web
compose up --detach --remove-orphans --wait --wait-timeout 240 || rollback
smoke_test || rollback

# Keep disk bounded: images from older releases are not needed once this one is healthy.
docker image prune --all --force --filter "until=168h" >/dev/null

echo "Deployed:"
echo "  api $API_IMAGE"
echo "  web $WEB_IMAGE"
