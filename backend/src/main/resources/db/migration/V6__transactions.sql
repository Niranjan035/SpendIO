-- SpendIO V6 - the transactions ledger
-- (ARCHITECTURE.md spec C.3 #10)
--
-- The table lands with Phase 3 so account balances, the opening ADJUSTMENT rows and
-- the balance-aggregation tests have a real ledger to read; the transactions module
-- (Phase 8) writes and serves it. `recurring_*` / `csv_*` columns are created here
-- without their foreign keys - those tables arrive in V7 and V10, which add the
-- constraints additively (ADR-0005). V10 must add them WITHOUT ON DELETE SET NULL:
-- csv_import_id / csv_import_row_id appear in chk_tx_import_pair, and MySQL 8
-- rejects that combination (error 3823).

CREATE TABLE transactions (
    id                         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id                    BIGINT UNSIGNED NOT NULL,
    account_id                 BIGINT UNSIGNED NOT NULL,
    category_id                BIGINT UNSIGNED NULL,
    type                       ENUM('INCOME','EXPENSE','TRANSFER','ADJUSTMENT') NOT NULL,
    -- The only column balance maths reads.
    direction                  ENUM('INFLOW','OUTFLOW') NOT NULL,
    amount_minor               BIGINT          NOT NULL,
    currency_code              CHAR(3)         NOT NULL,
    -- base->transaction currency, frozen at write time.
    exchange_rate              DECIMAL(20,10)  NOT NULL,
    base_amount_minor          BIGINT          NOT NULL,
    fx_rate_id                 BIGINT UNSIGNED NULL,
    occurred_on                DATE            NOT NULL,
    posted_at                  DATETIME(3)     NULL,
    status                     ENUM('POSTED','PENDING','VOID') NOT NULL DEFAULT 'POSTED',
    description                VARCHAR(255)    NULL,
    merchant                   VARCHAR(160)    NULL,
    payee                      VARCHAR(160)    NULL,
    notes                      TEXT            NULL,
    transfer_group_id          CHAR(36)        CHARACTER SET ascii COLLATE ascii_bin NULL,
    counterparty_transaction_id BIGINT UNSIGNED NULL,
    reversal_of_transaction_id BIGINT UNSIGNED NULL,
    reversed_at                DATETIME(3)     NULL,
    reversal_reason            VARCHAR(255)    NULL,
    -- FKs added by V7 (recurring tables) / V10 (csv import tables).
    recurring_transaction_id   BIGINT UNSIGNED NULL,
    recurring_run_id           BIGINT UNSIGNED NULL,
    csv_import_id              BIGINT UNSIGNED NULL,
    csv_import_row_id          BIGINT UNSIGNED NULL,
    dedupe_key                 VARCHAR(64)     CHARACTER SET ascii COLLATE ascii_bin NULL,
    deleted_at                 DATETIME(3)     NULL,
    created_at                 DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at                 DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by                 BIGINT          NULL,
    updated_by                 BIGINT          NULL,
    version                    INT             NOT NULL DEFAULT 0,
    -- Balance maths: sign lives in direction, never in amount_minor (spec C.3 #10).
    signed_amount_minor        BIGINT AS (CASE WHEN direction = 'INFLOW'  THEN amount_minor      ELSE -amount_minor      END) STORED,
    signed_base_amount_minor   BIGINT AS (CASE WHEN direction = 'INFLOW'  THEN base_amount_minor ELSE -base_amount_minor END) STORED,
    PRIMARY KEY (id),
    CONSTRAINT fk_tx_user      FOREIGN KEY (user_id)      REFERENCES users (id)       ON DELETE CASCADE,
    CONSTRAINT fk_tx_account   FOREIGN KEY (account_id)   REFERENCES accounts (id)    ON DELETE RESTRICT,
    -- spec C.3 #10 asks for ON DELETE SET NULL here; MySQL 8 rejects a SET NULL FK
    -- whose column also appears in a CHECK constraint (error 3823) and category_id /
    -- counterparty / reversal_of are all CHECK columns. Categories are soft-deleted
    -- with reassignTo, so the action never fires in practice - NO ACTION keeps the
    -- checks and blocks an accidental hard delete of a referenced row instead.
    CONSTRAINT fk_tx_category  FOREIGN KEY (category_id)  REFERENCES categories (id),
    CONSTRAINT fk_tx_currency  FOREIGN KEY (currency_code) REFERENCES currencies (code),
    CONSTRAINT fk_tx_fx_rate   FOREIGN KEY (fx_rate_id)   REFERENCES fx_rates (id)    ON DELETE SET NULL,
    CONSTRAINT fk_tx_counterparty FOREIGN KEY (counterparty_transaction_id) REFERENCES transactions (id),
    CONSTRAINT fk_tx_reversal_of   FOREIGN KEY (reversal_of_transaction_id)   REFERENCES transactions (id),
    CONSTRAINT chk_tx_amount_positive   CHECK (amount_minor > 0),
    CONSTRAINT chk_tx_base_positive     CHECK (base_amount_minor > 0),
    CONSTRAINT chk_tx_rate_positive     CHECK (exchange_rate > 0),
    CONSTRAINT chk_tx_type_direction    CHECK (
        (type = 'INCOME'  AND direction = 'INFLOW')
     OR (type = 'EXPENSE' AND direction = 'OUTFLOW')
     OR (type IN ('TRANSFER','ADJUSTMENT'))),
    CONSTRAINT chk_tx_category_required CHECK (
        (type IN ('INCOME','EXPENSE') AND category_id IS NOT NULL)
     OR (type IN ('TRANSFER','ADJUSTMENT'))),
    CONSTRAINT chk_tx_transfer_pair     CHECK (
        type <> 'TRANSFER' OR (transfer_group_id IS NOT NULL AND counterparty_transaction_id IS NOT NULL)),
    CONSTRAINT chk_tx_adjustment_solo   CHECK (type <> 'ADJUSTMENT' OR category_id IS NULL),
    -- Original row carries reversed_at; the mirrored row carries reversal_of (never both).
    CONSTRAINT chk_tx_original_not_reversed CHECK (reversed_at IS NULL OR reversal_of_transaction_id IS NULL),
    CONSTRAINT chk_tx_reversal_marked       CHECK (reversal_of_transaction_id IS NULL OR reversed_at IS NOT NULL),
    CONSTRAINT chk_tx_import_pair        CHECK ((csv_import_id IS NULL) = (csv_import_row_id IS NULL)),
    -- Voided rows are excluded from category reports.
    CONSTRAINT chk_tx_void_category      CHECK (status <> 'VOID' OR category_id IS NULL)
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Nullable dedupe keys: many NULLs are fine in a MySQL unique index.
CREATE UNIQUE INDEX uk_tx_dedupe      ON transactions (user_id, dedupe_key);
CREATE INDEX idx_tx_user_date         ON transactions (user_id, occurred_on DESC, id DESC);
CREATE INDEX idx_tx_account_date      ON transactions (user_id, account_id, occurred_on DESC);
CREATE INDEX idx_tx_category_date     ON transactions (user_id, category_id, occurred_on DESC);
CREATE INDEX idx_tx_type_date         ON transactions (user_id, type, occurred_on DESC);
CREATE INDEX idx_tx_recurring         ON transactions (recurring_transaction_id, occurred_on);
CREATE INDEX idx_tx_import            ON transactions (csv_import_id, csv_import_row_id);
CREATE INDEX idx_tx_transfer          ON transactions (transfer_group_id);
CREATE INDEX idx_tx_dedupe_probe      ON transactions (user_id, occurred_on, amount_minor, currency_code);
CREATE INDEX idx_tx_base_date         ON transactions (user_id, signed_base_amount_minor, occurred_on);
CREATE FULLTEXT INDEX idx_tx_text     ON transactions (description, merchant, payee, notes);
