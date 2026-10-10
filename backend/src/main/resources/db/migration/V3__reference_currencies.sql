-- SpendIO V3 - currency reference table and the ISO-4217 seed (ARCHITECTURE.md Phase 1, spec C.3 #6)
--
-- Read-only from the application: no migration ever updates a row, a release may only
-- add codes. `users.default_currency_code` gets its foreign key here, after the table
-- it points at exists.

CREATE TABLE currencies (
    code       CHAR(3)     NOT NULL,
    name       VARCHAR(64) NOT NULL,
    minor_unit TINYINT     NOT NULL DEFAULT 2,
    symbol     VARCHAR(8)  NULL,
    is_active  TINYINT(1)  NOT NULL DEFAULT 1,
    sort_order SMALLINT    NOT NULL DEFAULT 0,
    PRIMARY KEY (code),
    CONSTRAINT chk_currencies_minor_unit CHECK (minor_unit BETWEEN 0 AND 4)
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE users
    ADD CONSTRAINT fk_users_default_currency
    FOREIGN KEY (default_currency_code) REFERENCES currencies (code);

INSERT INTO currencies (code, name, minor_unit, symbol, is_active, sort_order) VALUES
    ('USD', 'US Dollar',           2, '$',    1,   1),
    ('EUR', 'Euro',                2, '€',    1,   2),
    ('GBP', 'British Pound',       2, '£',    1,   3),
    ('JPY', 'Japanese Yen',        0, '¥',    1,   4),
    ('CNY', 'Chinese Yuan',        2, '¥',    1,   5),
    ('INR', 'Indian Rupee',        2, '₹',    1,   6),
    ('AUD', 'Australian Dollar',   2, 'A$',   1,   7),
    ('CAD', 'Canadian Dollar',     2, 'C$',   1,   8),
    ('CHF', 'Swiss Franc',         2, 'CHF',  1,   9),
    ('NZD', 'New Zealand Dollar',  2, 'NZ$',  1,  10),
    ('SGD', 'Singapore Dollar',    2, 'S$',   1,  11),
    ('HKD', 'Hong Kong Dollar',    2, 'HK$',  1,  12),
    ('KRW', 'South Korean Won',    0, '₩',    1,  13),
    ('TRY', 'Turkish Lira',        2, '₺',    1,  14),
    ('RUB', 'Russian Ruble',       2, '₽',    1,  15),
    ('BRL', 'Brazilian Real',      2, 'R$',   1,  16),
    ('MXN', 'Mexican Peso',        2, 'Mex$', 1,  17),
    ('ZAR', 'South African Rand',  2, 'R',    1,  18),
    ('SEK', 'Swedish Krona',       2, 'kr',   1,  19),
    ('NOK', 'Norwegian Krone',     2, 'kr',   1,  20),
    ('DKK', 'Danish Krone',        2, 'kr',   1,  21),
    ('PLN', 'Polish Zloty',        2, 'zł',   1,  22),
    ('CZK', 'Czech Koruna',        2, 'Kč',   1,  23),
    ('HUF', 'Hungarian Forint',    2, 'Ft',   1,  24),
    ('ILS', 'Israeli New Shekel',  2, '₪',    1,  25),
    ('THB', 'Thai Baht',           2, '฿',    1,  26),
    ('IDR', 'Indonesian Rupiah',   2, 'Rp',   1,  27),
    ('MYR', 'Malaysian Ringgit',   2, 'RM',   1,  28),
    ('PHP', 'Philippine Peso',     2, '₱',    1,  29),
    ('VND', 'Vietnamese Dong',     0, '₫',    1,  30),
    ('AED', 'UAE Dirham',          2, 'AED',  1,  31),
    ('SAR', 'Saudi Riyal',         2, 'SAR',  1,  32),
    ('QAR', 'Qatari Riyal',        2, 'QAR',  1,  33),
    ('KWD', 'Kuwaiti Dinar',       3, 'KD',   1,  34),
    ('BHD', 'Bahraini Dinar',      3, 'BD',   1,  35),
    ('OMR', 'Omani Rial',          3, 'OMR',  1,  36),
    ('JOD', 'Jordanian Dinar',     3, 'JD',   1,  37),
    ('EGP', 'Egyptian Pound',      2, 'E£',   1,  38),
    ('NGN', 'Nigerian Naira',      2, '₦',    1,  39),
    ('KES', 'Kenyan Shilling',     2, 'KSh',  1,  40),
    ('GHS', 'Ghanaian Cedi',       2, 'GH₵',  1,  41),
    ('MAD', 'Moroccan Dirham',     2, 'MAD',  1,  42),
    ('TWD', 'New Taiwan Dollar',   2, 'NT$',  1,  43),
    ('PKR', 'Pakistani Rupee',     2, 'Rs',   1,  44),
    ('BDT', 'Bangladeshi Taka',    2, '৳',    1,  45),
    ('LKR', 'Sri Lankan Rupee',    2, 'Rs',   1,  46),
    ('UAH', 'Ukrainian Hryvnia',   2, '₴',    1,  47),
    ('CLP', 'Chilean Peso',        0, 'CLP$', 1,  48),
    ('COP', 'Colombian Peso',      2, 'COL$', 1,  49),
    ('PEN', 'Peruvian Sol',        2, 'S/',   1,  50),
    ('ARS', 'Argentine Peso',      2, '$',    1,  51),
    ('ISK', 'Icelandic Krona',     0, 'kr',   1,  52),
    ('RON', 'Romanian Leu',        2, 'lei',  1,  53),
    ('BGN', 'Bulgarian Lev',       2, 'lev',  1,  54),
    ('HRK', 'Croatian Kuna',       2, 'kn',   0,  55);
