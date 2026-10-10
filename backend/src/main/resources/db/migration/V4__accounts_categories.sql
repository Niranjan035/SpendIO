-- SpendIO V4 - accounts and categories
-- (ARCHITECTURE.md Phase 3 / Phase 4, spec C.3 #8-#9)
--
-- Both tables land in V4 because the migration plan assigns the file name and the ER
-- graph to this pair: `accounts` is built by Phase 3, `categories` is consumed by the
-- Phase 4 module (which adds the default-catalogue seed later, in V12).

CREATE TABLE accounts (
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id               BIGINT UNSIGNED NOT NULL,
    name                  VARCHAR(80)     NOT NULL,
    type                  ENUM('CASH','BANK','CREDIT_CARD','WALLET','INVESTMENT','LOAN') NOT NULL,
    currency_code         CHAR(3)         NOT NULL,
    opening_balance_minor BIGINT          NOT NULL DEFAULT 0,
    opening_balance_date  DATE            NULL,
    institution           VARCHAR(80)     NULL,
    last4                 VARCHAR(4)      NULL,
    credit_limit_minor    BIGINT          NULL,
    billing_day           TINYINT         NULL,
    due_day               TINYINT         NULL,
    icon                  VARCHAR(40)     NULL,
    color                 CHAR(7)         NULL,
    notes                 VARCHAR(500)    NULL,
    is_primary            TINYINT(1)      NOT NULL DEFAULT 0,
    status                ENUM('ACTIVE','ARCHIVED') NOT NULL DEFAULT 'ACTIVE',
    archived_at           DATETIME(3)     NULL,
    deleted_at            DATETIME(3)     NULL,
    created_at            DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at            DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by            BIGINT          NULL,
    updated_by            BIGINT          NULL,
    version               INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_accounts_user     FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_accounts_currency FOREIGN KEY (currency_code) REFERENCES currencies (code),
    -- Soft delete renames the row to "<name> (archived YYYY-MM)" so the simple
    -- per-user unique stays enforced for every visible name (spec C.3 #8 note).
    CONSTRAINT chk_accounts_credit_limit CHECK (credit_limit_minor IS NULL OR credit_limit_minor >= 0),
    CONSTRAINT chk_accounts_billing_day  CHECK (billing_day IS NULL OR billing_day BETWEEN 1 AND 31),
    CONSTRAINT chk_accounts_due_day      CHECK (due_day IS NULL OR due_day BETWEEN 1 AND 31)
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE UNIQUE INDEX uk_accounts_user_name ON accounts (user_id, name);
CREATE INDEX idx_accounts_user ON accounts (user_id, status, deleted_at);
CREATE INDEX idx_accounts_type ON accounts (user_id, type);
CREATE INDEX idx_accounts_name  ON accounts (user_id, name);

CREATE TABLE categories (
    id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    -- NULL = system default visible to every user (spec C.3 #9).
    user_id              BIGINT UNSIGNED NULL,
    parent_id            BIGINT UNSIGNED NULL,
    name                 VARCHAR(60)     NOT NULL,
    type                 ENUM('INCOME','EXPENSE') NOT NULL,
    icon                 VARCHAR(40)     NULL,
    color                CHAR(7)         NULL,
    is_default           TINYINT(1)      NOT NULL DEFAULT 0,
    is_system            TINYINT(1)      NOT NULL DEFAULT 0,
    sort_order           SMALLINT        NOT NULL DEFAULT 0,
    exclude_from_budgets TINYINT(1)      NOT NULL DEFAULT 0,
    status               ENUM('ACTIVE','ARCHIVED') NOT NULL DEFAULT 'ACTIVE',
    archived_at          DATETIME(3)     NULL,
    deleted_at           DATETIME(3)     NULL,
    created_at           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by           BIGINT          NULL,
    updated_by           BIGINT          NULL,
    version              INT             NOT NULL DEFAULT 0,
    parent_key           BIGINT AS (IFNULL(parent_id, 0)) STORED,
    -- IFNULL(user_id, 0) so system rows (user_id NULL) share one unique scope per
    -- (type, parent, name) and every user gets their own (spec C.3 #9).
    system_scope_key     BIGINT AS (IFNULL(user_id, 0)) STORED,
    PRIMARY KEY (id),
    -- Both FKs are deliberately actionless. MySQL 8 rejects referential actions
    -- (CASCADE / SET NULL, error 1215) on columns that feed STORED generated columns
    -- participating in an index - which is exactly what parent_id/system_scope_key do
    -- for uk_category_system. Categories are soft-deleted (deleted_at) and user
    -- deletion is soft too, so neither action would ever fire in the product anyway;
    -- the FKs still enforce referential integrity, with NO ACTION additionally
    -- blocking an accidental hard delete of a referenced row. The spec's self-parent
    -- CHECK (parent_id <> id) is also impossible on MySQL (errors 3818/3823), so it is
    -- enforced in the Phase 4 service layer.
    CONSTRAINT fk_categories_user   FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories (id),
    CONSTRAINT chk_categories_type   CHECK (type IN ('INCOME','EXPENSE'))
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE UNIQUE INDEX uk_category_system ON categories (system_scope_key, type, parent_key, name);
CREATE INDEX idx_categories_user   ON categories (user_id, type, status);
CREATE INDEX idx_categories_parent ON categories (parent_id);
