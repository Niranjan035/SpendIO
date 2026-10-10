-- SpendIO V2 - users, preferences, sessions and auth token tables
-- (ARCHITECTURE.md Phase 1 / Phase 2, spec C.3 #1-#5)
--
-- The default-currency foreign key is added by V3 (which creates `currencies`
-- first) so that migrations stay additive and ordered (ADR-0005).

CREATE TABLE users (
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    email                 VARCHAR(320)    NOT NULL,
    password_hash         VARCHAR(100)    NOT NULL,
    first_name            VARCHAR(80)     NULL,
    last_name             VARCHAR(80)     NULL,
    display_name          VARCHAR(160)    NULL,
    timezone              VARCHAR(64)     NOT NULL DEFAULT 'UTC',
    locale                VARCHAR(16)     NOT NULL DEFAULT 'en-GB',
    default_currency_code CHAR(3)         NOT NULL,
    is_active             TINYINT(1)      NOT NULL DEFAULT 1,
    is_email_verified     TINYINT(1)      NOT NULL DEFAULT 0,
    failed_login_attempts SMALLINT        NOT NULL DEFAULT 0,
    locked_until          DATETIME(3)     NULL,
    last_login_at         DATETIME(3)     NULL,
    password_changed_at   DATETIME(3)     NULL,
    must_change_password  TINYINT(1)      NOT NULL DEFAULT 0,
    deleted_at            DATETIME(3)     NULL,
    created_at            DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at            DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by            BIGINT          NULL,
    updated_by            BIGINT          NULL,
    version               INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT chk_users_email        CHECK (email <> ''),
    CONSTRAINT chk_users_failed_login CHECK (failed_login_attempts >= 0)
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE UNIQUE INDEX uk_users_email  ON users (email);
CREATE INDEX        idx_users_locked ON users (locked_until);
CREATE INDEX        idx_users_active ON users (is_active, deleted_at);

CREATE TABLE user_preferences (
    id                        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id                   BIGINT UNSIGNED NOT NULL,
    theme                     ENUM('SYSTEM','LIGHT','DARK')     NOT NULL DEFAULT 'SYSTEM',
    date_format               VARCHAR(20)     NOT NULL DEFAULT 'yyyy-MM-dd',
    first_day_of_week         TINYINT         NOT NULL DEFAULT 1,
    decimal_style             ENUM('DOT','COMMA')               NOT NULL DEFAULT 'DOT',
    group_separator           VARCHAR(4)      NOT NULL DEFAULT ',',
    currency_symbol_position  ENUM('BEFORE','AFTER')            NOT NULL DEFAULT 'BEFORE',
    number_format             VARCHAR(24)     NOT NULL DEFAULT '#,##0.00',
    budget_alert_threshold_pct TINYINT        NOT NULL DEFAULT 80,
    goal_reminder_days        SMALLINT        NOT NULL DEFAULT 30,
    recurring_reminder_days   SMALLINT        NOT NULL DEFAULT 3,
    email_digest_enabled      TINYINT(1)      NOT NULL DEFAULT 0,
    reduced_motion            TINYINT(1)      NOT NULL DEFAULT 0,
    created_at                DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at                DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by                BIGINT          NULL,
    updated_by                BIGINT          NULL,
    version                   INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_preferences_user (user_id),
    CONSTRAINT chk_prefs_threshold      CHECK (budget_alert_threshold_pct BETWEEN 1 AND 100),
    CONSTRAINT chk_prefs_first_day      CHECK (first_day_of_week BETWEEN 1 AND 7),
    CONSTRAINT chk_prefs_goal_days      CHECK (goal_reminder_days >= 0),
    CONSTRAINT chk_prefs_recurring_days CHECK (recurring_reminder_days >= 0),
    CONSTRAINT fk_user_preferences_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_sessions (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id                 BIGINT UNSIGNED NOT NULL,
    session_uuid            CHAR(36)        NOT NULL,
    family_id               CHAR(36)        NOT NULL,
    refresh_token_hash      CHAR(64)        NOT NULL,
    issued_at               DATETIME(3)     NOT NULL,
    expires_at              DATETIME(3)     NOT NULL,
    last_used_at            DATETIME(3)     NULL,
    revoked_at              DATETIME(3)     NULL,
    revocation_reason       ENUM('LOGOUT','ROTATED','REUSE_DETECTED','PASSWORD_CHANGE','ADMIN','EXPIRED') NULL,
    replaced_by_session_id  BIGINT UNSIGNED NULL,
    ip_address              VARCHAR(45)     NULL,
    user_agent              VARCHAR(255)    NULL,
    device_label            VARCHAR(80)     NULL,
    created_at              DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT chk_sessions_expiry CHECK (expires_at > issued_at),
    CONSTRAINT fk_sessions_user    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_sessions_replaced FOREIGN KEY (replaced_by_session_id) REFERENCES user_sessions (id) ON DELETE SET NULL
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE UNIQUE INDEX uk_sessions_hash  ON user_sessions (refresh_token_hash);
CREATE INDEX        idx_sessions_user  ON user_sessions (user_id, revoked_at);
CREATE INDEX        idx_sessions_family ON user_sessions (family_id);
CREATE INDEX        idx_sessions_expiry ON user_sessions (expires_at);

CREATE TABLE password_reset_tokens (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id      BIGINT UNSIGNED NOT NULL,
    token_hash   CHAR(64)        NOT NULL,
    requested_ip VARCHAR(45)     NULL,
    expires_at   DATETIME(3)     NOT NULL,
    used_at      DATETIME(3)     NULL,
    created_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_prt_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE UNIQUE INDEX uk_prt_hash   ON password_reset_tokens (token_hash);
CREATE INDEX        idx_prt_user   ON password_reset_tokens (user_id);
CREATE INDEX        idx_prt_expiry ON password_reset_tokens (expires_at);

CREATE TABLE verification_tokens (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id     BIGINT UNSIGNED NOT NULL,
    purpose     ENUM('EMAIL_VERIFY','EMAIL_CHANGE') NOT NULL,
    token_hash  CHAR(64)        NOT NULL,
    payload_json JSON            NULL,
    expires_at  DATETIME(3)     NOT NULL,
    consumed_at DATETIME(3)     NULL,
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_verification_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE UNIQUE INDEX uk_verification_hash   ON verification_tokens (token_hash);
CREATE INDEX        idx_verification_user   ON verification_tokens (user_id);
CREATE INDEX        idx_verification_expiry ON verification_tokens (expires_at);
