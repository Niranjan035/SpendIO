-- SpendIO V5 - FX rate table
-- (ARCHITECTURE.md Phase 5 groundwork, spec C.3 #7)
--
-- The table lands ahead of the fx module because V6 transactions references it
-- (fx_rate_id FK) and Phase 3 balance tests exercise the ledger. Rates themselves
-- arrive later: the Phase 5 refresh job and manual overrides write the rows.

CREATE TABLE fx_rates (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    base_currency_code  CHAR(3)         NOT NULL,
    quote_currency_code CHAR(3)         NOT NULL,
    rate                DECIMAL(20,10)  NOT NULL,
    rate_date           DATE            NOT NULL,
    provider            VARCHAR(40)     NOT NULL,
    is_manual           TINYINT(1)      NOT NULL DEFAULT 0,
    fetched_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_fx_rates_base  FOREIGN KEY (base_currency_code)  REFERENCES currencies (code),
    CONSTRAINT fk_fx_rates_quote FOREIGN KEY (quote_currency_code) REFERENCES currencies (code)
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- One rate per pair, day and provider; manual overrides live in the 'manual' provider.
CREATE UNIQUE INDEX uk_fx_pair_date ON fx_rates (base_currency_code, quote_currency_code, rate_date, provider);
CREATE INDEX idx_fx_lookup ON fx_rates (base_currency_code, quote_currency_code, rate_date DESC);
