-- V11 used TINYINT for several short-typed JPA fields, but Hibernate maps short to
-- SMALLINT and schema-validation failed at startup. Widening here keeps already-
-- applied V11 databases consistent without rewriting their checksum.
ALTER TABLE budget_alerts
    MODIFY threshold_pct SMALLINT NOT NULL;

ALTER TABLE budgets
    MODIFY alert_threshold_pct SMALLINT NOT NULL;

ALTER TABLE goals
    MODIFY priority SMALLINT NOT NULL DEFAULT 0;

ALTER TABLE goals
    MODIFY milestone_pct SMALLINT NOT NULL DEFAULT 90;