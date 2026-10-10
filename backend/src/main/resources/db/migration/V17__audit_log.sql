-- SpendIO V17 - audit trail (ARCHITECTURE.md C.3 #22, D.15, Phase 14).
-- Append-only record of security- and data-affecting actions. The user FK is
-- ON DELETE SET NULL so the trail survives a hard purge of the account (C.4).

CREATE TABLE audit_log (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id     BIGINT UNSIGNED NULL,
    actor_type  ENUM('USER','SYSTEM','JOB') NOT NULL,
    action      VARCHAR(60)     NOT NULL,
    entity_type VARCHAR(40)     NULL,
    entity_id   BIGINT UNSIGNED NULL,
    before_json JSON            NULL,
    after_json  JSON            NULL,
    ip_address  VARCHAR(45)     NULL,
    user_agent  VARCHAR(255)    NULL,
    trace_id    VARCHAR(32)     NULL,
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_audit_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT chk_audit_entity
        CHECK (entity_type IS NOT NULL OR entity_id IS NULL)
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_audit_user ON audit_log (user_id, created_at DESC);
CREATE INDEX idx_audit_entity ON audit_log (entity_type, entity_id);
CREATE INDEX idx_audit_created ON audit_log (created_at);

-- New revocation reason for the account-deletion flow (Phase 14). Extends the V2
-- enum additively; existing values are untouched (ADR-0005).
ALTER TABLE user_sessions
    MODIFY COLUMN revocation_reason
        ENUM('LOGOUT','ROTATED','REUSE_DETECTED','PASSWORD_CHANGE','ADMIN','EXPIRED','ACCOUNT_DELETED') NULL;
