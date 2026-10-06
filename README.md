# SpendIO

Personal finance and expense tracker — a modular monolith (Spring Boot 3, Java 21) with a
React SPA. The full design, the 146-endpoint REST contract, the 23-table schema and the
implementation checklist live in [`ARCHITECTURE.md`](ARCHITECTURE.md), which is the single
source of truth for this repository.

## Layout

| Path        | What it is                                                        |
| ----------- | ----------------------------------------------------------------- |
| `backend/`  | Spring Boot 3 modular monolith (feature-first packages)          |
| `frontend/` | React 19 + TypeScript + Vite SPA                                  |
| `docs/adr/` | Architecture decision records                                      |
| `infra/`    | Deployment manifests                                              |
| `.github/`  | CI pipeline                                                       |

## Prerequisites

* JDK 21+ (the build targets `--release 21`)
* Maven wrapper — `./mvnw` bootstraps itself, no global Maven needed
* Node 22+ for the frontend
* Docker (optional) — for the local stack and Testcontainers-backed tests

## Run the backend

```bash
cd backend
./mvnw spring-boot:run          # dev profile, http://localhost:8080
./mvnw spring-boot:run "-Dspring-boot.run.profiles=prod"
```

Health: `GET http://localhost:8080/actuator/health`.

## Run the tests

```bash
cd backend
./mvnw test
./mvnw verify        # adds ArchUnit + coverage gate
```

Integration tests need **MySQL 8.x on port 3307** (database `spendio_test`). Resolution
order in `MySqlTestSupport`:

1. `SPENDIO_TEST_DB_URL` / `SPENDIO_TEST_DB_USERNAME` / `SPENDIO_TEST_DB_PASSWORD`
2. Testcontainers `mysql:8.4` when Docker is reachable
3. A local MySQL listening on `localhost:3307`

Without any of the three, DB-backed tests **skip** (they never fail the build) —
`@EnabledIf("com.spendio.support.MySqlTestSupport#available")` on each DB test class.

Fast local loop against an existing local instance:

```sql
CREATE DATABASE spendio_test CHARACTER SET utf8mb4;
-- root with an empty password is what the local fallback uses
```

## Run the whole stack

```bash
cp .env.example .env
docker compose up --build      # backend :8080, frontend :5173, mailpit :8025, mysql :3306
```

Production-shaped overlay:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

## Environment

All configuration is environment-first with safe development defaults; see
[`.env.example`](.env.example). Secrets are never committed — `.env` is git-ignored.
`SPENDIO_JWT_SECRET` must be at least 32 bytes and is required by the `prod` profile.

## Conventions

* Money is stored as `BIGINT` minor units + ISO-4217 code (ADR-0002) — never floats.
* Schema changes are additive Flyway migrations (ADR-0005); `ddl-auto` is `validate`.
* Every user-owned query is filtered by the caller's `user_id`; ownership misses are 404.
* Lint/type-check before pushing: `./mvnw verify` and `npm run build` are what CI runs.
