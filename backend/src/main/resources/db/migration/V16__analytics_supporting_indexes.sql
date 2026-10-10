-- SpendIO V16 - analytics supporting structures (ARCHITECTURE.md Phase 13, C.3 #11)
--
-- account_snapshots is the daily balance rollup that lets net-worth history read a
-- few rows per day instead of re-summing the whole ledger. Written nightly by
-- AccountSnapshotJob (and on demand by AccountSnapshotService.ensureToday for the
-- dashboard); rebuildable at any time from `transactions`.

CREATE TABLE account_snapshots (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    account_id          BIGINT UNSIGNED NOT NULL,
    user_id             BIGINT UNSIGNED NOT NULL,
    snapshot_date       DATE         NOT NULL,
    balance_minor       BIGINT       NOT NULL,
    balance_base_minor  BIGINT       NOT NULL,
    base_currency_code  VARCHAR(3)   NOT NULL,
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_by          BIGINT       NULL,
    updated_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    updated_by          BIGINT       NULL,
    version             INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_snapshot_account FOREIGN KEY (account_id) REFERENCES accounts (id) ON DELETE CASCADE,
    CONSTRAINT fk_snapshot_user    FOREIGN KEY (user_id)    REFERENCES users (id)    ON DELETE CASCADE,
    CONSTRAINT uk_snapshot UNIQUE (account_id, snapshot_date)
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_snapshot_user_date ON account_snapshots (user_id, snapshot_date);
