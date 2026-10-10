package com.spendio.recurring.repository;

import com.spendio.recurring.domain.RecurringRun;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecurringRunRepository extends JpaRepository<RecurringRun, Long> {

    Optional<RecurringRun> findByRecurringTransactionIdAndScheduledFor(Long recurringTransactionId,
            LocalDate scheduledFor);

    boolean existsByRecurringTransactionIdAndScheduledFor(Long recurringTransactionId, LocalDate scheduledFor);

    List<RecurringRun> findByRecurringTransactionIdAndScheduledForIn(Long recurringTransactionId,
            Collection<LocalDate> scheduledFor);

    Page<RecurringRun> findByRecurringTransactionId(Long recurringTransactionId, Pageable pageable);

    /** Re-attempts of a FAILED row serialize on the row itself (ARCHITECTURE.md J.5). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RecurringRun r "
            + "where r.recurringTransactionId = :ruleId and r.scheduledFor = :scheduledFor")
    Optional<RecurringRun> findForUpdate(@Param("ruleId") Long ruleId,
            @Param("scheduledFor") LocalDate scheduledFor);
}
