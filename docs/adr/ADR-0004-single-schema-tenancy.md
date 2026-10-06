# ADR-0004: Single schema with a `user_id` discriminator

**Status:** Accepted · 2026-10-06

**Context.** Multi-tenancy can be modelled per-schema, per-database or per-row.

**Decision.** One schema; every user-owned table carries `user_id` and every user-scoped
index leads with `user_id`. Tenancy is enforced in the query layer: specifications are
always AND-ed with the caller's user id, `TenantGuard` asserts ownership (missing or
foreign rows surface as **404**, never 403, so ownership is not observable), and an
`OwnershipIsolationIT` sweep exercises every owned resource.

**Consequences.** Trivial backups and joins for analytics, no connection-pool
multiplication, and one omission in a query becomes the top-priority class of bug — which
is exactly why the guard + IDOR test suite are mandatory.
