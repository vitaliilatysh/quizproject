#!/usr/bin/env bash
# One-time preparation of a fresh Ubuntu 24.04 droplet. Run as root:
#
#   curl -fsSL https://raw.githubusercontent.com/vitaliilatysh/quizproject/master/deploy/droplet/bootstrap.sh | bash -s -- "ssh-ed25519 AAAA... github-deploy"
#
# The argument is the PUBLIC half of the key the deploy workflow logs in with.
# It creates an unprivileged `deploy` user that can run docker and nothing else
# on the host, opens only SSH, HTTP and HTTPS, and creates /opt/quizproject.
set -euo pipefail

if [ "$(id -u)" -ne 0 ]; then
  echo "Run as root." >&2
  exit 1
fi
if [ "$#" -ne 1 ] || [[ "$1" != ssh-* ]]; then
  echo "usage: $0 \"<deploy public key>\"" >&2
  exit 64
fi
DEPLOY_KEY="$1"

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install --yes --no-install-recommends ca-certificates curl ufw

if ! command -v docker >/dev/null 2>&1; then
  curl -fsSL https://get.docker.com | sh
fi

id deploy >/dev/null 2>&1 || useradd --create-home --shell /bin/bash deploy
usermod --append --groups docker deploy
install -d -m 700 -o deploy -g deploy /home/deploy/.ssh
printf '%s\n' "$DEPLOY_KEY" > /home/deploy/.ssh/authorized_keys
chown deploy:deploy /home/deploy/.ssh/authorized_keys
chmod 600 /home/deploy/.ssh/authorized_keys

install -d -m 750 -o deploy -g deploy /opt/quizproject

ufw default deny incoming
ufw default allow outgoing
ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable

echo
echo "Done. Next, as root: create /opt/quizproject/.env from deploy/droplet/env.example"
echo "(chown deploy:deploy, chmod 600), then run the Deploy to DigitalOcean workflow."
