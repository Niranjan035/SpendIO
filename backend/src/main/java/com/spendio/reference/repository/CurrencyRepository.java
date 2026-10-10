package com.spendio.reference.repository;

import com.spendio.reference.domain.Currency;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Read-only access to the currency table. The whole table is under 300 rows and is
 * cached in front of it (ARCHITECTURE.md D.4), so no projections are needed.
 */
public interface CurrencyRepository extends JpaRepository<Currency, String> {

    @Query("select c from Currency c where c.active = true order by c.sortOrder asc, c.code asc")
    List<Currency> findAllActive();
}
