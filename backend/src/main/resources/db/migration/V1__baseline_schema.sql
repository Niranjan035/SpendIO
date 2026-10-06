-- SpendIO V1 - baseline schema (ARCHITECTURE.md Phase 0)
--
-- Establishes the schema contract before any application tables exist:
-- an empty but migrated database. V2+ introduce tables; migrations are
-- additive only (ADR-0005).
--
-- Conventions that V1 makes explicit (ARCHITECTURE.md D.16):
--   * charset   utf8mb4, collation utf8mb4_0900_ai_ci (case-insensitive)
--   * money     BIGINT minor units + ISO 4217 code column - never DECIMAL/FLOAT
--   * dates     business dates DATE (timezone-free); instants DATETIME(3) UTC
--   * keys      BIGINT auto-increment; every user-scoped index leads with user_id
--   * audit     created_at/updated_at/created_by/updated_by on mutable tables,
--               version column on aggregate roots, deleted_at for soft delete

ALTER DATABASE CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
