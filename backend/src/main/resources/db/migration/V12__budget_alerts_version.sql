-- budget_alerts.version was omitted from V11; BaseEntity's optimistic-lock column
-- is mandatory for every JPA entity (ARCHITECTURE.md C.2), so Hibernate's
-- schema-validation failed on startup. Added separately so already-applied V11
-- databases pick it up without a checksum rewrite.
ALTER TABLE budget_alerts
    ADD COLUMN version INT NOT NULL DEFAULT 0 AFTER acknowledged_at;