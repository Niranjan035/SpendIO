package com.spendio.budgets.repository;

import com.spendio.budgets.domain.Budget;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BudgetRepository extends JpaRepository<Budget, Long>, JpaSpecificationExecutor<Budget> {

    Optional<Budget> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

    /** Update/archive/rollover serialise on the budget row itself. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Budget b where b.id = :id and b.userId = :userId and b.deletedAt is null")
    Optional<Budget> findOwnedForUpdate(@Param("userId") Long userId, @Param("id") Long id);

    boolean existsByUserIdAndNameAndDeletedAtIsNull(Long userId, String name);

    boolean existsByUserIdAndNameAndDeletedAtIsNullAndIdNot(Long userId, String name, Long id);

    /**
     * The overall-budget overlap guard (D.10): MySQL unique indexes treat NULL
     * category_id as distinct, so "at most one category-less budget per overlapping
     * period" is this existence check plus {@code BUDGET_OVERLAPPING_OVERALL}.
     * Arguments are the candidate window: an existing row overlaps when it starts on
     * or before {@code to} and ends on or after {@code from}.
     */
    @Query("select count(b) > 0 from Budget b where b.userId = :userId and b.category is null "
            + "and b.deletedAt is null and b.periodStart <= :to and b.periodEnd >= :from")
    boolean existsOverlappingOverallBudget(@Param("userId") Long userId,
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    List<Budget> findByUserIdAndDeletedAtIsNullAndStatusOrderByPeriodStartDesc(Long userId, Budget.Status status);

    /** ACTIVE budgets whose window contains {@code onDate} - the `/current` sweep. */
    @Query("select b from Budget b where b.userId = :userId and b.deletedAt is null and b.status = :status "
            + "and b.periodStart <= :onDate and b.periodEnd >= :onDate")
    List<Budget> findByUserIdAndDeletedAtIsNullAndStatusAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(
            @Param("userId") Long userId, @Param("status") Budget.Status status, @Param("onDate") LocalDate onDate);

    /** Rollover candidates whose window has already closed. */
    List<Budget> findByDeletedAtIsNullAndStatusAndRolloverEnabledTrueAndPeriodEndLessThan(
            Budget.Status status, LocalDate today);

    /** Active, alert-enabled budgets of one user whose window overlaps the evaluation range. */
    @Query("select b from Budget b where b.userId = :userId and b.deletedAt is null and b.status = :status "
            + "and b.alertEnabled = true and b.periodStart <= :to and b.periodEnd >= :from")
    List<Budget> findAlertCandidates(@Param("userId") Long userId, @Param("status") Budget.Status status,
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** The same sweep across every user - how the nightly job discovers whose budgets to check. */
    @Query("select b from Budget b where b.deletedAt is null and b.status = :status "
            + "and b.alertEnabled = true and b.periodStart <= :to and b.periodEnd >= :from")
    List<Budget> findAllAlertCandidates(@Param("status") Budget.Status status,
            @Param("from") LocalDate from, @Param("to") LocalDate to);
}
