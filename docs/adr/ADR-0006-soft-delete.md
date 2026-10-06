# ADR-0006: Soft delete with a partial unique index

**Status:** Accepted · 2026-10-06

**Context.** Financial history must remain auditable, but users expect "delete" to remove
an item from every list.

**Decision.** User-visible deletion is a **soft delete**: `deleted_at TIMESTAMP NULL` on
deletable tables, never a physical `DELETE`. Queries filter `deleted_at IS NULL` through
the shared specification helpers, never by hand at each call site. Uniqueness is expressed
with **partial indexes** (`UNIQUE (user_id, lower(name)) WHERE deleted_at IS NULL`) so a
deleted row does not block re-creating the same name. Physical deletion is reserved for
the scheduled purge job and for erasure requests.

**Consequences.** 404 semantics stay clean (a soft-deleted row is simply not found),
re-creation works, and the audit trail survives until the purge job runs.
