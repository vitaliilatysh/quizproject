# Deploying to a DigitalOcean droplet

The stack runs on one droplet with `docker compose`: Caddy (the only thing that
listens on the internet), the frontend, the API, MySQL 8.4 and Redis. The
Kubernetes manifests next to this directory are a separate target and are not
used here.

```
internet ──► Caddy :80/:443 ─┬─ /api/*  ──► api :8081 ──► mysql, redis
                             └─ /       ──► web :8080
```

The API's actuator port (9081), MySQL and Redis are not published.

## Files

| File | Purpose |
|---|---|
| `compose.yaml` | The services, healthchecks and resource limits |
| `Caddyfile` | Routes `/api` to the backend and everything else to the frontend |
| `runtime-config.js` | Points the frontend at its own origin |
| `env.example` | Template for the secrets file kept **only** on the droplet |
| `deploy.sh` | Runs on the droplet: start the new images, health-check, roll back on failure |
| `bootstrap.sh` | One-time preparation of a fresh droplet |
| `../../.github/workflows/deploy-droplet.yml` | The workflow that ties it together |

## One-time setup

1. **Create a deploy key** on your machine (no passphrase, used only for this):

   ```bash
   ssh-keygen -t ed25519 -N '' -C github-deploy -f deploy_key
   ```

2. **Prepare the droplet.** On the droplet, as root, run `bootstrap.sh` with the
   public key. It installs Docker, creates an unprivileged `deploy` user, opens
   only SSH, HTTP and HTTPS in `ufw`, and creates `/opt/quizproject`:

   ```bash
   curl -fsSL https://raw.githubusercontent.com/vitaliilatysh/quizproject/master/deploy/droplet/bootstrap.sh \
     | bash -s -- "$(cat deploy_key.pub)"
   ```

   Ports 80 and 443 are published by Docker, which bypasses `ufw` for published
   ports. That is intended here, since they are the only ones published. Add a
   DigitalOcean Cloud Firewall as well if you want a second layer.

3. **Create the secrets file** on the droplet:

   ```bash
   cp env.example /opt/quizproject/.env   # fill it in
   chown deploy:deploy /opt/quizproject/.env && chmod 600 /opt/quizproject/.env
   ```

   Generate the secrets with `openssl rand -base64 32` (JWT) and
   `openssl rand -hex 24` (passwords). `PUBLIC_ORIGIN` must match what the
   browser shows in the address bar, or CORS will reject the frontend.

4. **Add the GitHub secrets** to the `production` environment of the backend
   repository (Settings → Environments → production):

   | Secret | Value |
   |---|---|
   | `DROPLET_HOST` | the droplet's IP or hostname |
   | `DROPLET_USER` | `deploy` |
   | `DROPLET_SSH_KEY` | the private key, `deploy_key` |
   | `DROPLET_SSH_KNOWN_HOSTS` | output of `ssh-keyscan -t ed25519 <host>` |

   To require an approval before each deploy, add a required reviewer to the
   same environment.

5. **Run it once by hand**: Actions → *Deploy to DigitalOcean* → Run workflow.
   Both images must already be published (`sha-<commit>` for the backend,
   `main` for the frontend).

6. **Optional, deploy on every merge**: set the repository variable
   `DROPLET_DEPLOY_ENABLED` to `true`. The workflow then follows a successful
   *Backend container delivery* on `master`.

## HTTPS

Without a domain the site is served over plain HTTP on the IP. For HTTPS, point
a DNS A record at the droplet, then set in `.env`:

```
SITE_ADDRESS=quiz.example.com
PUBLIC_ORIGIN=https://quiz.example.com
```

and run the workflow again. Caddy obtains and renews the certificate itself.

## What a deploy does

1. Resolves the backend and frontend tags to digests, once, and verifies the
   cosign signature of each against its own repository's `container.yml`.
2. Copies the compose files to `/opt/quizproject` (never `.env`).
3. On the droplet, `deploy.sh` starts the stack on those digests and waits for
   every healthcheck, then requests the frontend's `/healthz` and the API's
   public `/api/v1/quizzes/summary` through the compose network.
4. If any step fails, the previous images are started again and the run fails.

## Operating it

```bash
cd /opt/quizproject
docker compose --env-file .env --env-file images.env ps
docker compose --env-file .env --env-file images.env logs -f api
```

Roll back by running the workflow with an older `api_tag` / `web_tag` (any
`sha-<commit>` that was published).

## Data

MySQL lives in the `mysql-data` Docker volume on the droplet's disk. A deploy
never removes it. It is **not backed up by default**: enable DigitalOcean
droplet backups, and see `docs/database-recovery.md` for logical dumps.
