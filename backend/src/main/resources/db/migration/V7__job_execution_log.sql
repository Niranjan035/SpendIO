-- SpendIO V7 - scheduler job log (ARCHITECTURE.md A.5)
-- Written by every @Scheduled worker (first user: the Phase 5 FX refresh job) so a
-- run can be audited: what ran, when, how long it took and whether it failed.

CREATE TABLE job_execution_log (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    job_name      VARCHAR(80)     NOT NULL,
    status        VARCHAR(16)     NOT NULL,
    started_at    DATETIME(3)     NOT NULL,
    finished_at   DATETIME(3)     NULL,
    affected_rows INT UNSIGNED    NOT NULL DEFAULT 0,
    detail        TEXT            NULL,
    PRIMARY KEY (id),
    INDEX idx_job_execution_log_name_started (job_name, started_at DESC)
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;