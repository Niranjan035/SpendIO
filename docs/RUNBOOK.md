# SpendIO Runbook

Operational procedures for the SpendIO backend and frontend. Commands assume you are at
the repository root unless stated otherwise.

## Local / single-node topology

- Backend: Spring Boot 3 (`backend/`, `mvnw`), virtual threads, `server.port` `8080`.
- Frontend: Vite dev server (`frontend/`, `5173`), served as static assets in prod.
- Database: MySQL 8, Flyway-migrated on startup (`ddl-auto=validate`).

### Required environment

| Variable | Purpose |
|---|---|
| `SPENDIO_DB_URL` / `SPENDIO_DB_USERNAME` / `SPENDIO_DB_PASSWORD` | Datasource |
| `SPENDIO_JWT_SECRET` | JWT signing key (≥ 32 chars, **no default in prod**; app fails fast if missing) |
| `SPENDIO_JWT_KID` | Key id advertised in the token header (rotation) |
| `SPENDIO_MAIL_*` | SMTP transport |
| `SPENDIO_CORS_ALLOWED_ORIGINS` | Comma-separated SPA origins (**required in prod**) |
| `SPENDIO_WEB_BASE_URL` | Public SPA URL used in email links (**required in prod**) |
| `SPENDIO_DB_POOL_SIZE` / `SPENDIO_DB_MIN_IDLE` | HikariCP pool sizing (default 20 / 5) |
| `SPENDIO_RATE_LIMIT_*` | Auth/sensitive endpoint rate-limit window and limits |
| `SPENDIO_PURGE_GRACE_DAYS` | Days before a soft-deleted user is hard-deleted (default 30) |

Production (`prod` profile) turns on `server.forward-headers-strategy=framework` and secure
cookies, so terminate TLS in the ingress/proxy and forward `X-Forwarded-Proto`.

## Start / restart

```powershell
# backend (from backend/)
$env:JAVA_HOME="C:\Program Files\Java\jdk-26.0.1"
.\mvnw.cmd spring-boot:run

# frontend (from frontend/)
npm run dev
```

Graceful shutdown is enabled (`server.shutdown=graceful`, 20 s drain). A rolling restart
on one node is safe: in-flight requests complete before the process exits.

## Build and verify

```powershell
# backend: compile, unit + architecture tests
$env:JAVA_HOME="C:\Program Files\Java\jdk-26.0.1"
.\mvnw.cmd -q verify

# frontend: types, lint, tests, bundle
npm run typecheck
npm run lint
npm run test:run
npm run build
```

DB-backed tests require a reachable MySQL (Testcontainers or `SPENDIO_TEST_DB_URL`); they
skip fail-open when Docker is unavailable.

## Scheduled jobs

Every `@Scheduled` worker writes a `job_execution_log` row (start, finish, status, error).
Cron expressions are property-overridable:

| Job | Property | Default |
|---|---|---|
| Recurring generation | `spendio.recurring.cron` | `0 */15 * * * *` |
| FX refresh | `spendio.fx.refresh-cron` | `0 15 3 * * *` |
| Account snapshots | `spendio.accounts.snapshot-cron` | `0 10 0 * * *` |
| Budget rollover | `spendio.budgets.rollover-cron` | `0 30 0 1 * *` |
| Budget alerts | `spendio.budgets.alert-cron` | `0 15 0 * * *` |
| Goal reconciliation | `spendio.goals.reconciliation-cron` | `0 45 0 * * *` |
| User purge | `spendio.users.purge-cron` | `0 30 4 * * *` |
| Audit retention | `spendio.audit.retention-cron` | `0 10 4 * * *` |
| Notification retention | `spendio.notifications.retention-cron` | `0 5 3 * * *` |

To pause a single job without a deploy, override its cron to a date far in the future
(e.g. `0 0 0 1 1 *`, as the test profile does). To replay recurring generation, invoke
the executor's entry point directly (the scheduler claim uses `FOR UPDATE SKIP LOCKED`
plus a unique `(recurring_transaction_id, scheduled_for)` constraint, so re-running is
idempotent and duplicates are logged `SKIPPED_DUPLICATE`).

## Backup and restore (MySQL)

Use the scripts in `infra/scripts/` — they record binlog coordinates for
point-in-time recovery, verify the archive, and refuse to leave a partial file behind.

```bash
# nightly full backup (single transaction, safe on a live primary)
DB_HOST=mysql DB_USER=root DB_PASSWORD="$MYSQL_ROOT_PASSWORD" DB_NAME=spendio \
  infra/scripts/backup.sh /var/backups/spendio

# restore drill into a scratch schema (non-destructive; ALLOW_DROP=0 by default)
DB_HOST=mysql DB_USER=root DB_PASSWORD="$MYSQL_ROOT_PASSWORD" DB_NAME=spendio_restore \
  infra/scripts/restore.sh /var/backups/spendio/spendio-<stamp>.sql.gz
```

`restore.sh` verifies the restored schema has the expected tables and a `users` row count;
it exits non-zero if the dump looks empty. The CI `backup-restore-drill` job runs the same
pair on every push.

**Restore drill cadence: weekly.** Restore into a scratch schema, boot the app against it
(`SPENDIO_DB_URL` pointing at the scratch DB), and confirm health + a login. Keep binlogs
(`infra/mysql/my.cnf` enables `log_bin` with `ROW` + `sync_binlog=1`) for PITR: replay them
on top of a full dump to reach a point in time.

### Point-in-time recovery

1. Restore the most recent full dump (above).
2. Replay binlogs up to the incident timestamp:
   `mysqlbinlog --start-datetime="..." --stop-datetime="..." mysql-bin.* | mysql`.
3. Boot the app against the recovered schema and run `infra/scripts/smoke-test.sh`.

## Revoke sessions

- A single session: `DELETE /api/v1/auth/sessions/{sessionId}`.
- Every session: `POST /api/v1/auth/logout-all` (or change the password, which revokes all
  other sessions as part of the flow).

To force-logout every user after a suspected token compromise, rotate `SPENDIO_JWT_SECRET`
(and bump `SPENDIO_JWT_KID`) and restart; all outstanding access tokens become invalid
immediately, and refresh reuse detection handles stolen refresh tokens.

## Account deletion and purge

`DELETE /api/v1/users/me` soft-deletes and anonymises immediately. The hard delete runs
from `UserPurgeJob` after `spendio.users.purge-grace-days` (default 30). To act on a
specific account before the job fires, set the grace period to a small value and restart,
or purge the row directly only as a documented break-glass step.

## Deployment

### Container topology

- `frontend` — nginx serving the SPA and proxying `/api` + `/actuator` to the backend
  (`BACKEND_UPSTREAM`, default `http://backend:8080`).
- `backend` — Spring Boot (`prod` profile), non-root, layered image, health on
  `/actuator/health/readiness`.
- `mysql` — MySQL 8 with binlogs enabled (`infra/mysql/my.cnf`).
- `docker-compose.monitoring.yml` — optional Prometheus + Grafana overlay.

### Compose (single host)

```bash
cp .env.example .env      # fill in every required secret first
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build --wait
BASE_URL=https://app.example.com infra/scripts/smoke-test.sh

# with monitoring
docker compose -f docker-compose.yml -f docker-compose.prod.yml \
  -f docker-compose.monitoring.yml up -d
```

`--wait` blocks until every healthcheck is green; `smoke-test.sh` then proves the probes
and API answer. The `:?` guards in `docker-compose.prod.yml` abort the deploy if a required
secret is unset.

### Kubernetes

```bash
kubectl apply -f infra/k8s/configmap.yaml
kubectl apply -f infra/k8s/secret.example.yaml   # or create the Secret out-of-band
kubectl apply -f infra/k8s/backend-deployment.yaml
kubectl apply -f infra/k8s/frontend-deployment.yaml
kubectl apply -f infra/k8s/hpa.yaml
```

Backend and frontend Deployments use `maxUnavailable: 0` for zero-downtime rolling
updates; a `preStop` sleep plus graceful shutdown drains in-flight requests. Readiness
gates traffic to warmed pods; liveness restarts wedged ones.

### Rolling restart (no downtime)

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --no-deps backend
```

### Rollback

```bash
# compose: pin the previous image tag and recreate
IMAGE_TAG=<previous-sha> docker compose ... up -d --no-deps backend
# k8s
kubectl rollout undo deployment/spendio-backend
```

The CD workflows tag images by commit SHA, so any prior release is redeployable by SHA.
Flyway migrations are additive (never renumbered); a rollback of application code does not
require a schema rollback.

### HTTPS termination

Terminate TLS at the ingress (nginx/ALB/Traefik) in front of `frontend`; the backend sets
secure cookies and honours `X-Forwarded-Proto`, so forwarded requests are treated as HTTPS.
Enable HSTS **at the TLS terminator** (not in the shared frontend nginx config, which is
also used for plain-HTTP local development):

```
Strict-Transport-Security: max-age=31536000; includeSubDomains
```

## Observability

| Signal | Where |
|---|---|
| Metrics | `GET /actuator/prometheus` (public), scraped by `infra/monitoring/prometheus.yml` |
| Health | `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness` |
| Dashboard | Grafana (`infra/monitoring/grafana-dashboard.json`), provisioned automatically |
| Alerts | `infra/monitoring/alert.rules.yml` |
| Logs | JSON (`logback-spring-prod.xml`), secrets/tokens masked |

Alert rules cover: backend down, health not `UP`, p95 > 500 ms / > 2 s, 5xx ratio > 1 %,
HikariCP pool > 90 % and JVM heap > 90 %.

## Performance budget

The heavy read paths hold a p95 < 500 ms / p99 < 1.5 s budget under load. See
`perf/README.md` for the exact per-endpoint targets and how to run the k6 scenarios
(`perf/k6/read-paths.js`). Every list endpoint is bounded (`PageRequestFactory` caps
`size` at 100; analytics windows are range-validated), so no endpoint returns an
unbounded result set.

## Secrets management

- Locally: `.env` (git-ignored). In compose: environment from `.env`.
- Kubernetes: a `spendio-secrets` Secret (see `infra/k8s/secret.example.yaml`), applied
  out-of-band — never committed filled in.
- Generate `SPENDIO_JWT_SECRET` with `openssl rand -base64 48`.

## Incident checklist

1. Identify scope from `audit_log` and `job_execution_log` (filter by `trace_id`).
2. Rotate `SPENDIO_JWT_SECRET` / DB credentials if either may be exposed.
3. Revoke sessions (`logout-all`) for affected users.
4. Snapshot the database before any destructive remediation.
5. Record the timeline in the incident channel and update this runbook with what changed.
