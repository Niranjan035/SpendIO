package com.spendio.sharedkernel.money;

/**
 * Resolves the user's reporting (base) currency, which lives in the users module.
 * Accounts is only allowed to depend on this port (ARCHITECTURE.md B.3 - no module
 * may reach into another module's repository, so the implementation is provided by
 * users and injected here).
 */
public interface BaseCurrencyPort {

    String baseCurrencyCode(Long userId);
}