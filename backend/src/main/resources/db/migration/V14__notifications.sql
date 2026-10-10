-- Phase 11 (ARCHITECTURE.md C.3, D.13): the notification inbox and its per-type
-- per-channel preferences. budget_alerts.notification_id (added with V11) finally
-- gets its FK here; the alert row is optional per notification, so soft/hard deletes
-- null it out via ON DELETE SET NULL.

CREATE TABLE notifications (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id       BIGINT UNSIGNED NOT NULL,
    type          ENUM('BUDGET_ALERT','GOAL_MILESTONE','GOAL_REMINDER',
                       'RECURRING_DUE','RECURRING_FAILED','IMPORT_COMPLETED',
                       'IMPORT_FAILED','SYSTEM') NOT NULL,
    severity      ENUM('INFO','LOW','MEDIUM','HIGH','CRITICAL') NOT NULL,
    title         VARCHAR(160)    NOT NULL,
    body          VARCHAR(2000)   NOT NULL,
    entity_type   VARCHAR(40)     NULL,
    entity_id     BIGINT UNSIGNED NULL,
    action_url    VARCHAR(500)    NULL,
    payload       JSON            NULL,
    dedupe_key    VARCHAR(255)    NULL,
    read_at       DATETIME(3)     NULL,
    dismissed_at  DATETIME(3)     NULL,
    deleted_at    DATETIME(3)     NULL,
    created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_by    BIGINT          NULL,
    updated_by    BIGINT          NULL,
    version       INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_notification_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_notification_deep_link
        CHECK ((entity_type IS NULL) = (entity_id IS NULL))
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- The idempotency gate behind "exactly one notification per real event" (K.10):
-- replayed kernel events collide on (user_id, dedupe_key) and are swallowed.
CREATE UNIQUE INDEX uk_notification_dedupe ON notifications (user_id, dedupe_key);
CREATE INDEX idx_notification_user_created ON notifications (user_id, created_at DESC);
CREATE INDEX idx_notification_inbox ON notifications (user_id, read_at, dismissed_at, deleted_at);

CREATE TABLE notification_preferences (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id      BIGINT UNSIGNED NOT NULL,
    type         ENUM('BUDGET_ALERT','GOAL_MILESTONE','GOAL_REMINDER',
                      'RECURRING_DUE','RECURRING_FAILED','IMPORT_COMPLETED',
                      'IMPORT_FAILED','SYSTEM') NOT NULL,
    channel      ENUM('IN_APP','EMAIL') NOT NULL,
    enabled      TINYINT(1)      NOT NULL DEFAULT 1,
    min_severity ENUM('INFO','LOW','MEDIUM','HIGH','CRITICAL') NULL,
    created_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_by   BIGINT          NULL,
    updated_by   BIGINT          NULL,
    version      INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_notification_pref_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_notification_pref_severity
        CHECK (enabled = 1 OR min_severity IS NOT NULL)
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE UNIQUE INDEX uk_notification_pref
    ON notification_preferences (user_id, type, channel);
CREATE INDEX idx_notification_pref_user
    ON notification_preferences (user_id);

-- Back-links the alert to the notification that rendered it (ON DELETE SET NULL so
-- retaining/purging a notification never cascades into the budget alert).
ALTER TABLE budget_alerts
    ADD CONSTRAINT fk_alert_notification
        FOREIGN KEY (notification_id) REFERENCES notifications (id) ON DELETE SET NULL;

CREATE INDEX idx_alert_notification ON budget_alerts (notification_id);