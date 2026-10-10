-- Phase 9 + Phase 10 (ARCHITECTURE.md C.3 #14-#17): budgets, budget alerts, goals and
-- goal contributions. The spec planned one "budgets_goals" file (it expected the V8
-- slot, which the ledger fix-ups took); additive migration order puts it here.
-- budget_alerts.notification_id gets its FK in the Phase 11 notifications migration -
-- the referenced table does not exist yet, and a dangling nullable column is harmless.

CREATE TABLE budgets (
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id               BIGINT UNSIGNED NOT NULL,
    name                  VARCHAR(80)     NOT NULL,
    category_id           BIGINT UNSIGNED NULL,
    period_type           ENUM('WEEKLY','MONTHLY','QUARTERLY','YEARLY','CUSTOM') NOT NULL,
    period_start          DATE         NOT NULL,
    period_end            DATE         NOT NULL,
    amount_minor          BIGINT       NOT NULL,
    currency_code         CHAR(3)      NOT NULL,
    base_amount_minor     BIGINT       NOT NULL,
    rollover_enabled      TINYINT(1)   NOT NULL DEFAULT 0,
    rollover_amount_minor BIGINT       NOT NULL DEFAULT 0,
    alert_threshold_pct   TINYINT      NOT NULL DEFAULT 80,
    alert_enabled         TINYINT(1)   NOT NULL DEFAULT 1,
    include_subcategories TINYINT(1)   NOT NULL DEFAULT 1,
    status                ENUM('ACTIVE','ARCHIVED') NOT NULL DEFAULT 'ACTIVE',
    deleted_at            DATETIME(3)  NULL,
    created_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_by            BIGINT       NULL,
    updated_by            BIGINT       NULL,
    version               INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_budget_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_budget_category
        FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE SET NULL,
    CONSTRAINT fk_budget_currency
        FOREIGN KEY (currency_code) REFERENCES currencies (code) ON DELETE RESTRICT,
    CONSTRAINT chk_budget_amount
        CHECK (amount_minor > 0),
    CONSTRAINT chk_budget_period
        CHECK (period_end >= period_start),
    CONSTRAINT chk_budget_threshold
        CHECK (alert_threshold_pct BETWEEN 1 AND 100),
    CONSTRAINT chk_budget_rollover
        CHECK (rollover_amount_minor >= 0),
    CONSTRAINT chk_budget_status
        CHECK (status IN ('ACTIVE','ARCHIVED'))
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- NULL category_id (an overall budget) is "distinct" per MySQL unique semantics, so
-- the service enforces the one-overall-budget-per-overlapping-period rule (D.10).
CREATE UNIQUE INDEX uk_budget_name ON budgets (user_id, name);
CREATE UNIQUE INDEX uk_budget_period ON budgets (user_id, category_id, period_start, period_end);
CREATE INDEX idx_budget_user ON budgets (user_id, status);
CREATE INDEX idx_budget_window ON budgets (user_id, period_start, period_end);

CREATE TABLE budget_alerts (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    budget_id           BIGINT UNSIGNED NOT NULL,
    user_id             BIGINT UNSIGNED NOT NULL,
    period_start        DATE         NOT NULL,
    period_end          DATE         NOT NULL,
    threshold_pct       TINYINT      NOT NULL,
    budget_amount_minor BIGINT       NOT NULL,
    spent_amount_minor  BIGINT       NOT NULL,
    triggered_at        DATETIME(3)  NOT NULL,
    acknowledged_at     DATETIME(3)  NULL,
    notification_id     BIGINT UNSIGNED NULL,
    PRIMARY KEY (id),
    -- The hard guarantee behind "one alert per threshold per period" (K.5).
    UNIQUE KEY uk_budget_alert (budget_id, period_start, threshold_pct),
    INDEX idx_alert_user (user_id, triggered_at DESC),
    CONSTRAINT fk_alert_budget
        FOREIGN KEY (budget_id) REFERENCES budgets (id) ON DELETE CASCADE,
    CONSTRAINT fk_alert_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_alert_threshold
        CHECK (threshold_pct BETWEEN 1 AND 100),
    CONSTRAINT chk_alert_amounts
        CHECK (budget_amount_minor > 0 AND spent_amount_minor >= 0)
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE goals (
    id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id              BIGINT UNSIGNED NOT NULL,
    name                 VARCHAR(80)  NOT NULL,
    description          VARCHAR(500) NULL,
    target_amount_minor  BIGINT       NOT NULL,
    current_amount_minor BIGINT       NOT NULL DEFAULT 0,
    currency_code        CHAR(3)      NOT NULL,
    target_date          DATE         NULL,
    status               ENUM('ACTIVE','COMPLETED','PAUSED','CANCELLED') NOT NULL DEFAULT 'ACTIVE',
    linked_account_id    BIGINT UNSIGNED NULL,
    priority             TINYINT      NOT NULL DEFAULT 0,
    icon                 VARCHAR(40)  NULL,
    color                CHAR(7)      NULL,
    completed_at         DATETIME(3)  NULL,
    milestone_pct        TINYINT      NOT NULL DEFAULT 90,
    deleted_at           DATETIME(3)  NULL,
    created_at           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_by           BIGINT       NULL,
    updated_by           BIGINT       NULL,
    version              INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_goal_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_goal_account
        FOREIGN KEY (linked_account_id) REFERENCES accounts (id) ON DELETE SET NULL,
    CONSTRAINT fk_goal_currency
        FOREIGN KEY (currency_code) REFERENCES currencies (code) ON DELETE RESTRICT,
    CONSTRAINT chk_goal_target
        CHECK (target_amount_minor > 0),
    CONSTRAINT chk_goal_current
        CHECK (current_amount_minor >= 0 AND current_amount_minor <= target_amount_minor),
    CONSTRAINT chk_goal_completed
        CHECK (status <> 'COMPLETED' OR completed_at IS NOT NULL),
    CONSTRAINT chk_goal_milestone
        CHECK (milestone_pct BETWEEN 1 AND 100),
    CONSTRAINT chk_goal_priority
        CHECK (priority BETWEEN 0 AND 5)
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE UNIQUE INDEX uk_goal_name ON goals (user_id, name);
CREATE INDEX idx_goal_user ON goals (user_id, status);

-- current_amount_minor on goals is a cache of SUM(amount_minor) here; the nightly
-- GoalReconciliationJob repairs drift (C.3 #16 invariant, D.11).
CREATE TABLE goal_contributions (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    goal_id         BIGINT UNSIGNED NOT NULL,
    user_id         BIGINT UNSIGNED NOT NULL,
    amount_minor    BIGINT       NOT NULL,
    currency_code   CHAR(3)      NOT NULL,
    contributed_on  DATE         NOT NULL,
    method          ENUM('MANUAL','TRANSFER','ADJUSTMENT') NOT NULL,
    transaction_id  BIGINT UNSIGNED NULL,
    notes           VARCHAR(500) NULL,
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NULL,
    created_by      BIGINT       NULL,
    updated_by      BIGINT       NULL,
    version         INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_contrib_goal
        FOREIGN KEY (goal_id) REFERENCES goals (id) ON DELETE CASCADE,
    CONSTRAINT fk_contrib_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_contrib_transaction
        FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE SET NULL,
    CONSTRAINT fk_contrib_currency
        FOREIGN KEY (currency_code) REFERENCES currencies (code) ON DELETE RESTRICT,
    CONSTRAINT chk_contrib_amount
        CHECK (amount_minor <> 0)
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- One ledger row can back at most one contribution (transfer-linked idempotency).
CREATE UNIQUE INDEX uk_contrib_transaction ON goal_contributions (transaction_id);
CREATE INDEX idx_contrib_goal ON goal_contributions (goal_id, contributed_on DESC);
CREATE INDEX idx_contrib_user ON goal_contributions (user_id, contributed_on DESC);
