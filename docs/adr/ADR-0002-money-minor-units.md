# ADR-0002: Money as BIGINT minor units + ISO-4217 code

**Status:** Accepted · 2026-10-06

**Context.** Floating point produces rounding drift; `DECIMAL(18,2)` bakes in a two-decimal
scale that is wrong for JPY (0) and KWD (3).

**Decision.** Every persisted amount is a `BIGINT` count of minor units alongside an ISO
4217 code column; currency minor-unit digits live in the `currencies` reference table.
In Java the only permitted representation is the `Money` value object
(`long amountMinor` + `CurrencyCode`). Storage never uses `double`, `float` or
`BigDecimal`.

**FX.** `exchange_rate` and `base_amount_minor` are frozen at write time. Historical rows
are never recomputed when rates or the user's base currency change; revaluation is an
explicit, audited, opt-in action.

**Consequences.** Exact arithmetic and stable history; every aggregate must be written in
minor units, and formatting/parsing happens only at the edges (`MoneyFormatter`).
