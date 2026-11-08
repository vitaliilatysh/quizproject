# DigitalOcean Droplet deployment

The deployment target is the existing `quiz-project-prod` Droplet in `fra1`
(`164.92.163.71`, 2 vCPU / 4 GB RAM). No Kubernetes cluster or DigitalOcean API
token is needed by GitHub Actions. The workflow connects over SSH and uses the
public GHCR images already built and signed by container delivery.

Deploy **backend first, frontend second**. The backend creates the shared
`quizproject-production` network. MySQL and Redis stay on a separate internal
network with no published ports; API/actuator ports are not published either.
Caddy publishes 80/443, routes `/api/*` to the backend and serves the frontend
through its existing unprivileged nginx container. Frontend runtime configuration
uses the browser's origin, without rebuilding the image.

## Prepare the existing server once

Inspect existing containers, databases, occupied ports and backup coverage before
the first deployment. These files describe a **new** isolated Compose database;
they do not import or replace an existing application's data. Never run `down -v`
or remove existing Docker volumes to resolve a deployment problem.

Install Docker Engine and the Compose v2 plugin following the
[official Ubuntu instructions](https://docs.docker.com/engine/install/ubuntu/).
Use a dedicated SSH account, for example `deploy`, with access to Docker. Docker
access grants administrative control of the host, so restrict this account's
authorized keys to the deployment keys. Do not give it a password login.

As an administrator, create its deployment directory:

```sh
sudo install -d -m 0700 -o deploy -g deploy /opt/quizproject
```

Copy `deploy/digitalocean/prepare-env.py` from the backend repository to the
server. Run it as `deploy`, initially with the IP address if no domain is ready:

```sh
python3 prepare-env.py --environment production --origin http://164.92.163.71
```

This creates `/opt/quizproject/production/backend/.env` and
`/opt/quizproject/production/frontend/.env` with mode 0600. Database passwords,
Redis password and the JWT signing secret are generated **on the server** and are
never printed, copied to Actions or committed to Git. Existing files cause the
command to refuse; rerunning it cannot silently rotate database credentials.

For a domain already pointing to this Droplet, pass `--origin https://quiz.your-domain.com`
instead. Caddy obtains and renews certificates and persists them in Docker
volumes. When adding a domain later, update backend `PUBLIC_ORIGIN` and frontend
`SITE_ADDRESS` in the server's `.env` files and redeploy both components.

HTTP on an IP is for initial connectivity checks; configure a domain and HTTPS
before sending passwords or other sensitive data. The domain is not purchased
by these workflows. Only public 80/443 and the intended SSH port should be
reachable. MySQL, Redis, API and actuator have no host port mappings.

## Configure both GitHub repositories

In the `production` GitHub environment in **both** repositories, set:

| Type | Name | Value |
| --- | --- | --- |
| Variable | `DO_HOST` | `164.92.163.71` |
| Variable | `DO_SSH_USER` | `deploy` (default) |
| Variable | `DO_SSH_PORT` | `22` (default) |
| Secret | `DO_SSH_KEY` | Private key whose public key is authorized for `deploy` |
| Secret | `DO_KNOWN_HOSTS` | Verified OpenSSH known-hosts entry for the Droplet |

Verify the server host-key fingerprint through the DigitalOcean console or an
existing trusted SSH session before saving the known-hosts entry. The workflow
uses `StrictHostKeyChecking=yes` and never accepts an unverified `ssh-keyscan`
result. For nonstandard SSH ports, use the `[host]:port` known-hosts format.
Never paste the private key into a chat, command-line argument or Git file.

Existing GitHub environment protection remains effective. Retain production
reviewers where configured. Production is a manual **Deploy to DigitalOcean** dispatch, selecting
the default branch and `production`. The image tag defaults to `sha-<commit>`;
an older published tag can be selected for an explicit image rollback.

Automatic staging is off by default. To enable it, separately prepare the
`staging` environment and server, then set the **repository** variable
`DO_STAGING_ENABLED=true`. Do not enable a second full stack on the 4 GB
production Droplet without checking capacity and assigning distinct ports.

## What a deployment does

1. Resolve the selected published image to an immutable digest and verify its
   cosign signature came from this repository's default-branch delivery workflow.
2. Before production, require successful CI, SonarCloud and container delivery
   for both the signed image revision and deployment checkout. Frontend also
   requires full-stack E2E. Missing, skipped or failed release checks block it.
3. Copy only the reviewed Compose/proxy/release files to a new directory under
   `/opt/quizproject/<environment>/<component>/releases/<run>-<attempt>`.
4. Acquire the shared host lock, validate Compose without printing resolved
   secrets, pull the application image and wait for all service health checks.
5. Switch `current` only after success. On a failed start, reapply the previous
   release and wait for health. On the first failure there is no prior release;
   data volumes are left intact for diagnosis. No deploy removes volumes.

The two repositories share a server-side lock because GitHub concurrency groups
do not coordinate across repositories. Application deployments do not explicitly
pull newer MySQL/Redis/Caddy tags; review infrastructure upgrades separately.
Missing infrastructure images are pulled on first startup. Current single-host
Compose rollout can briefly interrupt requests; this is not a multi-node setup.

An image rollback does **not** undo Flyway database migrations. Review schema
compatibility before rolling back. MySQL data persists in the backend project's
named volume. Configure encrypted off-host backups and a tested restore schedule
before storing production data; the existing
[database recovery procedure](database-recovery.md) describes backup/restore
verification. Local Docker volume persistence is not a backup.

After deployment, verify `/healthz`, `/runtime-config.js`, `/api/v1/quizzes` and
login/attempt completion over the public HTTPS origin. Inspect failures using
`docker compose ... ps` and bounded service logs on the server. Do not print
resolved Compose environments or upload database dumps to Actions artifacts.
