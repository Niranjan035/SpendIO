package com.spendio.fx.repository;

import com.spendio.fx.domain.FxRate;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FxRateRepository extends JpaRepository<FxRate, Long>, JpaSpecificationExecutor<FxRate> {

    /**
     * Exact-pair resolution (ARCHITECTURE.md D.5 step 1): newest rate on or before the
     * requested date with manual overrides preferred over provider rows.
     */
    @Query("""
            select r from FxRate r
            where r.baseCurrencyCode = :base and r.quoteCurrencyCode = :quote and r.rateDate <= :date
            order by r.isManual desc, r.rateDate desc, r.fetchedAt desc
            """)
    List<FxRate> findLatestOnOrBefore(@Param("base") String base,
                                      @Param("quote") String quote,
                                      @Param("date") LocalDate date);

    /** Newest known row for the pair at any date - the stale fallback (step 5). */
    @Query("""
            select r from FxRate r
            where r.baseCurrencyCode = :base and r.quoteCurrencyCode = :quote
            order by r.isManual desc, r.rateDate desc, r.fetchedAt desc
            """)
    List<FxRate> findLatest(@Param("base") String base, @Param("quote") String quote);

    Optional<FxRate> findByBaseCurrencyCodeAndQuoteCurrencyCodeAndRateDateAndProvider(
            String base, String quote, LocalDate date, String provider);

    List<FxRate> findByBaseCurrencyCodeAndQuoteCurrencyCodeAndRateDateBetweenOrderByRateDateDescFetchedAtDesc(
            String base, String quote, LocalDate from, LocalDate to);

    List<FxRate> findByBaseCurrencyCodeAndQuoteCurrencyCodeOrderByRateDateDescFetchedAtDesc(
            String base, String quote);
}