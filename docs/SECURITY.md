# SpendIO Security

Threat-model summary for the SpendIO API and single-page app. It describes what the
system protects, the controls already in place, and the residual risks accepted for v1.
The authoritative design rationale lives in `ARCHITECTURE.md` section J.

## Scope and assets

| Asset | Where it lives | Sensitivity |
|---|---|---|
| Account credentials | `users` (BCrypt hash only) | Critical |
| Access tokens | Client memory (JWT, 15 min) | High |
| Refresh tokens | `user_sessions` (SHA-256 hash) + httpOnly cookie | High |
| Financial data | `accounts`, `transactions`, `budgets`, `goals` | High |
| Uploaded CSVs | `CsvStorage` (local disk / S3 SPI) | Medium |
| Audit trail | `audit_log` | Medium |

## Trust boundaries

- The browser is untrusted; the JWT in memory is reachable by injected script.
- The API is the only writer to the database. Every owned-entity query is scoped by
  `user_id`; `TenantGuard` covers cross-references, and `OwnershipIsolationIT` sweeps
  every endpoint with a second tenant's ids.
- Modules talk through services and the shared kernel. ArchUnit forbids cross-module
  repository access and undeclared module edges (see `ArchUnitTests`).

## Authentication and sessions

- **Access tokens:** HS256 pinned, `typ=access`, `jti`, 15-minute TTL, secret from the
  environment (`SPENDIO_JWT_SECRET`, minimum 32 characters enforced at startup) with a
  `kid` header for rotation. The `none` algorithm and algorithm confusion are rejected.
- **Refresh tokens:** opaque 256-bit random value stored only as a SHA-256 hash, rotated
  on every use, delivered in a `SameSite=Strict` httpOnly cookie. Presenting an already
  rotated token revokes the whole family (reuse detection).
- **Logout / logout-all:** revokes the presented session or every session for the user.
  Reverting is intentionally not possible.
- **Password change:** verifies the current password, enforces the password policy,
  revokes every other session, records an audit entry, and raises a security notification.

## Authorisation

- Ownership is enforced at the repository boundary. A missing `user_id` predicate is a
  release blocker; the IDOR sweep and ArchUnit rules guard against regressions.
- There are no admin endpoints in v1; the token carries identity, not authority.

## Input handling

- Request bodies bind to DTO records only (never entities); unknown fields are rejected.
- Parameter binding everywhere — no string-concatenated SQL.
- CSV formula injection is neutralised on export (`CsvFormulaSanitizer`); previews render
  cells as plain text.
- Uploads: 10 MB cap, extension + MIME + magic-byte checks, randomised storage keys,
  path-traversal normalisation.

## Privacy and data lifecycle

- **Export:** `POST /api/v1/users/me/export` returns a JSON envelope built from the same
  read models the UI uses.
- **Deletion:** `DELETE /api/v1/users/me` requires the password plus the typed
  confirmation `DELETE`. It anonymises the profile, revokes sessions, purges imports,
  detaches audit rows (`ON DELETE SET NULL`), and publishes `UserAccountDeletedEvent`.
- **Retention:** `UserPurgeJob` hard-deletes accounts past the grace period
  (`spendio.users.purge-grace-days`, default 30). `AuditRetentionJob` and
  `NotificationRetentionJob` trim their tables on their own schedules.

## Audit trail

`audit_log` is append-only from the application's perspective. Writers never store
secrets; snapshots (`before_json`, `after_json`) are already redacted. The viewer
(`GET /api/v1/audit-logs`) returns only the caller's rows.

## Logging

Logs scrub `password`, `token`, `authorization`, and `refreshTokenHash`. Email addresses
are the only PII written to application logs and are never combined with credentials.

## Residual risks accepted for v1

- Multi-factor authentication is out of scope (TOTP column reserved).
- Rate limiting is in-process (Bucket4j); a shared store is required before horizontal
  scaling.
- Uploaded files use local disk by default; the `CsvStorage` SPI is the migration path.

## Reporting

Report suspected vulnerabilities privately to the maintainers; do not open a public
issue. Include a minimal reproduction and the affected commit.
