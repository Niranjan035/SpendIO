-- ---------------------------------------------------------------------------
-- SpendIO MySQL bootstrap (ARCHITECTURE.md Phase 16).
--
-- Runs once on first container start (docker-entrypoint-initdb.d). The image
-- already creates MYSQL_DATABASE and MYSQL_USER from the environment; this
-- file makes the collation explicit and pins the app user to the schema.
-- Idempotent: safe to run again by hand.
-- ---------------------------------------------------------------------------

CREATE DATABASE IF NOT EXISTS spendio
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

-- utc timezone tables are populated by the image; fail open if not permitted.
CREATE USER IF NOT EXISTS 'spendio'@'%' IDENTIFIED BY 'spendio';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, INDEX, ALTER, REFERENCES, TRIGGER
  ON spendio.* TO 'spendio'@'%';
FLUSH PRIVILEGES;
