-- SpendIO V15 - the CSV import pipeline
-- (ARCHITECTURE.md spec D.12)
--
-- csv_imports is the job record; csv_import_rows holds one row per source line with
-- raw/parsed JSON and the pipeline state (PENDING -> VALID/INVALID/DUPLICATE ->
-- CONFIRMED -> CREATED/SKIPPED/FAILED/UNDONE). The transactions.csv_import_* columns
-- landed in V6; their foreign keys point here and are added last: both columns feed
-- chk_tx_import_pair, and MySQL 8 rejects a SET NULL FK on a CHECK-referenced column
-- (error 3823), so they use NO ACTION.

CREATE TABLE csv_imports (
    id                       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id                  BIGINT UNSIGNED NOT NULL,
    status                   ENUM('UPLOADED','PARSING','PARSED','MAPPING_REQUIRED','VALIDATING','READY','PROCESSING','COMPLETED','COMPLETED_WITH_ERRORS','FAILED') NOT NULL DEFAULT 'UPLOADED',
    original_filename        VARCHAR(255) NOT NULL,
    file_size_bytes          BIGINT UNSIGNED NOT NULL,
    stored_file_key          VARCHAR(500) NOT NULL,
    checksum                 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    detected_delimiter       VARCHAR(8)  NULL,
    detected_encoding        VARCHAR(32) NULL,
    header_row_number        INT UNSIGNED NULL,
    file_headers             JSON NULL,
    -- ColumnMapping (@Embeddable).
    date_column              VARCHAR(64) NULL,
    amount_column            VARCHAR(64) NULL,
    description_column       VARCHAR(64) NULL,
    debit_column             VARCHAR(64) NULL,
    credit_column            VARCHAR(64) NULL,
    category_column          VARCHAR(64) NULL,
    account_column           VARCHAR(64) NULL,
    currency_column          VARCHAR(64) NULL,
    type_column              VARCHAR(64) NULL,
    date_format              VARCHAR(32) NULL,
    amount_format            VARCHAR(16) NULL,
    column_mapping_json      JSON NULL,
    -- ImportOptions (@Embeddable).
    default_currency_code    CHAR(3) NULL,
    default_date_format      VARCHAR(32) NULL,
    target_account_id        BIGINT UNSIGNED NULL,
    create_missing_categories BOOLEAN NOT NULL DEFAULT FALSE,
    skip_duplicates          BOOLEAN NOT NULL DEFAULT TRUE,
    allow_partial_import     BOOLEAN NOT NULL DEFAULT TRUE,
    default_type             VARCHAR(16) NULL,
    -- Running totals mirrored back in ImportSummaryResponse.
    total_rows               INT UNSIGNED NOT NULL DEFAULT 0,
    valid_rows               INT UNSIGNED NOT NULL DEFAULT 0,
    invalid_rows             INT UNSIGNED NOT NULL DEFAULT 0,
    duplicate_rows           INT UNSIGNED NOT NULL DEFAULT 0,
    created_rows             INT UNSIGNED NOT NULL DEFAULT 0,
    skipped_rows             INT UNSIGNED NOT NULL DEFAULT 0,
    failed_rows              INT UNSIGNED NOT NULL DEFAULT 0,
    error_summary            VARCHAR(1000) NULL,
    started_at               DATETIME(3) NULL,
    completed_at             DATETIME(3) NULL,
    created_at               DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at               DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by               BIGINT NULL,
    updated_by               BIGINT NULL,
    version                  INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_import_user     FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_import_account  FOREIGN KEY (target_account_id) REFERENCES accounts (id),
    CONSTRAINT fk_import_currency FOREIGN KEY (default_currency_code) REFERENCES currencies (code),
    INDEX idx_import_user_status (user_id, status, id DESC)
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE csv_import_rows (
    id                       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    import_id                BIGINT UNSIGNED NOT NULL,
    -- `row_number` is a reserved word in MySQL 8 (window function); quoted here and
    -- by hibernate.auto_quote_keyword in generated SQL.
    `row_number`             INT UNSIGNED NOT NULL,
    status                   ENUM('PENDING','VALID','INVALID','DUPLICATE','CONFIRMED','SKIPPED','CREATED','FAILED','UNDONE') NOT NULL DEFAULT 'PENDING',
    raw_json                 JSON NOT NULL,
    parsed_json              JSON NULL,
    occurred_on              DATE NULL,
    amount_minor             BIGINT NULL,
    currency_code            CHAR(3) NULL,
    description              VARCHAR(255) NULL,
    account_id               BIGINT UNSIGNED NULL,
    category_id              BIGINT UNSIGNED NULL,
    type                     VARCHAR(16) NULL,
    notes                    TEXT NULL,
    matched_account_id       BIGINT UNSIGNED NULL,
    matched_category_id      BIGINT UNSIGNED NULL,
    matched_by               VARCHAR(32) NULL,
    confidence               DECIMAL(4,3) NULL,
    duplicate_of_row_id      BIGINT UNSIGNED NULL,
    duplicate_of_transaction_id BIGINT UNSIGNED NULL,
    created_transaction_id   BIGINT UNSIGNED NULL,
    errors_json              JSON NULL,
    deleted_at               DATETIME(3) NULL,
    created_at               DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at               DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by               BIGINT NULL,
    updated_by               BIGINT NULL,
    version                  INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_import_row_import        FOREIGN KEY (import_id) REFERENCES csv_imports (id) ON DELETE CASCADE,
    CONSTRAINT fk_import_row_account       FOREIGN KEY (account_id) REFERENCES accounts (id),
    CONSTRAINT fk_import_row_category      FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT fk_import_row_matched_account  FOREIGN KEY (matched_account_id) REFERENCES accounts (id),
    CONSTRAINT fk_import_row_matched_category FOREIGN KEY (matched_category_id) REFERENCES categories (id),
    CONSTRAINT fk_import_row_dup_tx        FOREIGN KEY (duplicate_of_transaction_id) REFERENCES transactions (id) ON DELETE SET NULL,
    CONSTRAINT fk_import_row_created_tx    FOREIGN KEY (created_transaction_id) REFERENCES transactions (id) ON DELETE SET NULL,
    UNIQUE KEY uk_import_row_number (import_id, `row_number`),
    INDEX idx_import_row_status (import_id, status, id),
    INDEX idx_import_row_duplicate (import_id, duplicate_of_row_id)
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE transactions
    ADD CONSTRAINT fk_tx_csv_import     FOREIGN KEY (csv_import_id)     REFERENCES csv_imports (id),
    ADD CONSTRAINT fk_tx_csv_import_row FOREIGN KEY (csv_import_row_id) REFERENCES csv_import_rows (id);