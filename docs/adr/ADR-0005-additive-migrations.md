# ADR-0005: Additive, versioned Flyway migrations

**Status:** Accepted · 2026-10-06

**Context.** Schema changes must be reviewable, repeatable and safe to apply to an
existing install.

**Decision.** Schema lives only in Flyway migrations under `db/migration`, one file per
step, `V<version>__<snake_case_description>.sql`. Every migration is **additive and
forward-only**: no `EDIT`/`DROP` of shipped migrations, no destructive column changes in
place — add the new object, backfill, switch readers, drop the old object in a later
version. `ddl-auto` stays at `validate`. CI applies the full history to a clean database
on every push (`MigrationSmokeTest`).

**Consequences.** Rollback is "deploy the previous app version against a schema that only
grew"; table/column renames cost two releases instead of one, which is the accepted price
of never losing user data.
