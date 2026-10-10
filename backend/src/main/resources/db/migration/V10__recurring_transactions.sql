-- Phase 8 (ARCHITECTURE.md C.3 #12/#13): recurring transactions + their execution log.
-- The V6 comment planned this file as V7, but the job_execution_log table took the V7
-- slot in Phase 2 and csv import tables are still pending, so the recurring tables land
-- here. `transactions.recurring_transaction_id` / `recurring_run_id` (V6) receive their
-- FKs at the bottom of this file; neither column appears in any transactions CHECK
-- constraint, so ON DELETE SET NULL cannot hit MySQL error 3823 (the warning in V6
-- applies to the csv_* columns, whose chk_tx_import_pair FKs must NOT use SET NULL).
-- `to_account_id` is added here because ARCHITECTURE.md J.5/roadmap phase 8 require
-- TRANSFER-type rules (spec D.9/E.9 request schemas omitted the column - documented gap).

CREATE TABLE recurring_transactions (
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id               BIGINT UNSIGNED NOT NULL,
    account_id            BIGINT UNSIGNED NOT NULL,
    to_account_id         BIGINT UNSIGNED NULL,
    category_id           BIGINT UNSIGNED NULL,
    type                  ENUM('INCOME','EXPENSE','TRANSFER') NOT NULL,
    direction             ENUM('INFLOW','OUTFLOW') NOT NULL,
    amount_minor          BIGINT       NOT NULL,
    currency_code         CHAR(3)      NOT NULL,
    exchange_rate         DECIMAL(20,10) NOT NULL,
    base_amount_minor     BIGINT       NOT NULL,
    description           VARCHAR(255) NULL,
    notes                 TEXT         NULL,
    frequency             ENUM('DAILY','WEEKLY','MONTHLY','YEARLY','CUSTOM') NOT NULL,
    interval_count        SMALLINT     NOT NULL DEFAULT 1,
    day_of_month          TINYINT      NULL,
    day_of_week           TINYINT      NULL,
    skip_weekends         TINYINT(1)   NOT NULL DEFAULT 0,
    start_date            DATE         NOT NULL,
    end_date              DATE         NULL,
    next_run_date         DATE         NOT NULL,
    last_run_date         DATE         NULL,
    status                ENUM('ACTIVE','PAUSED','COMPLETED','FAILED','CANCELLED') NOT NULL DEFAULT 'ACTIVE',
    auto_post             TINYINT(1)   NOT NULL DEFAULT 1,
    max_retries           SMALLINT     NOT NULL DEFAULT 3,
    retry_count           SMALLINT     NOT NULL DEFAULT 0,
    last_error            VARCHAR(500) NULL,
    failure_reason        VARCHAR(500) NULL,
    generated_count       INT          NOT NULL DEFAULT 0,
    target_occurrence_date DATE        NULL,
    deleted_at            DATETIME(3)  NULL,
    created_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_by            BIGINT       NULL,
    updated_by            BIGINT       NULL,
    version               INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_rec_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_rec_account
        FOREIGN KEY (account_id) REFERENCES accounts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_rec_to_account
        FOREIGN KEY (to_account_id) REFERENCES accounts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_rec_category
        FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE SET NULL,
    CONSTRAINT fk_rec_currency
        FOREIGN KEY (currency_code) REFERENCES currencies (code) ON DELETE RESTRICT,
    CONSTRAINT chk_rec_amount_positive
        CHECK (amount_minor > 0),
    CONSTRAINT chk_rec_interval_positive
        CHECK (interval_count > 0),
    CONSTRAINT chk_rec_end_after_start
        CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT chk_rec_monthly_day
        CHECK (frequency <> 'MONTHLY' OR day_of_month IS NULL OR day_of_month BETWEEN 1 AND 31),
    CONSTRAINT chk_rec_weekly_day
        CHECK (frequency <> 'WEEKLY' OR day_of_week IS NULL OR day_of_week BETWEEN 1 AND 7),
    INDEX idx_rec_due (status, next_run_date),
    INDEX idx_rec_user (user_id, status),
    INDEX idx_rec_account (account_id)
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- Execution log + idempotency guard: uk_run is the hard guarantee that a schedule can
-- never post twice for the same occurrence (ARCHITECTURE.md J.5, C.3 #13).
CREATE TABLE recurring_transaction_runs (
    id                       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    recurring_transaction_id BIGINT UNSIGNED NOT NULL,
    user_id                  BIGINT UNSIGNED NOT NULL,
    scheduled_for            DATE         NOT NULL,
    executed_at              DATETIME(3)  NULL,
    status                   ENUM('SUCCESS','SKIPPED','FAILED','SKIPPED_DUPLICATE','SKIPPED_PAUSED') NOT NULL,
    transaction_id           BIGINT UNSIGNED NULL,
    attempts                 SMALLINT     NOT NULL DEFAULT 0,
    error_message            VARCHAR(500) NULL,
    created_at               DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_run (recurring_transaction_id, scheduled_for),
    INDEX idx_run_user_date (user_id, scheduled_for DESC),
    CONSTRAINT fk_run_rule
        FOREIGN KEY (recurring_transaction_id) REFERENCES recurring_transactions (id) ON DELETE CASCADE,
    CONSTRAINT fk_run_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_run_transaction
        FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE SET NULL
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

ALTER TABLE transactions
    ADD CONSTRAINT fk_tx_recurring_rule
        FOREIGN KEY (recurring_transaction_id) REFERENCES recurring_transactions (id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_tx_recurring_run
        FOREIGN KEY (recurring_run_id) REFERENCES recurring_transaction_runs (id) ON DELETE SET NULL;
