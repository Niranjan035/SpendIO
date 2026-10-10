package com.spendio.goals.repository;

import com.spendio.goals.domain.Goal;
import com.spendio.sharedkernel.domain.enums.GoalStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Goal reads plus the two guarded writes that keep {@code current_amount_minor} and
 * the completion flag consistent under concurrency (ARCHITECTURE.md C.3 #16,
 * J.4): increments are single UPDATE statements whose WHERE clause re-checks the
 * bounds, so two simultaneous deposits serialise in the database instead of racing
 * a read-modify-write.
 */
public interface GoalRepository extends JpaRepository<Goal, Long> {

    Optional<Goal> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

    boolean existsByUserIdAndNameAndDeletedAtIsNull(Long userId, String name);

    boolean existsByUserIdAndNameAndDeletedAtIsNullAndIdNot(Long userId, String name, Long id);

    List<Goal> findByDeletedAtIsNullAndStatusNot(GoalStatus status);

    /** Phase 11 goal reminders: active goals whose target date falls inside a window. */
    List<Goal> findByStatusAndTargetDateBetween(GoalStatus status, java.time.LocalDate from,
            java.time.LocalDate to);

    @Query("select g from Goal g where g.userId = :userId and g.deletedAt is null "
            + "and (:status is null or g.status = :status) "
            + "and (:q is null or lower(g.name) like lower(concat('%', :q, '%')))")
    Page<Goal> search(@Param("userId") Long userId, @Param("status") GoalStatus status,
            @Param("q") String q, Pageable pageable);

    /**
     * Optimistic-by-construction deposit/withdrawal: succeeds only while the result
     * stays inside {@code [0, target]} and the goal is ACTIVE; 0 rows means the guard
     * rejected it (bounds or status changed underneath), and the caller re-checks to
     * raise the right error.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Goal g set g.currentAmountMinor = g.currentAmountMinor + :delta, g.version = g.version + 1 "
            + "where g.id = :id and g.userId = :userId and g.deletedAt is null and g.status = :activeStatus "
            + "and g.currentAmountMinor + :delta >= 0 "
            + "and g.currentAmountMinor + :delta <= g.targetAmountMinor")
    int addCurrent(@Param("id") Long id, @Param("userId") Long userId, @Param("delta") long delta,
            @Param("activeStatus") GoalStatus activeStatus);

    /** Auto-complete as one guarded statement (no race between "reached target" and "status"). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Goal g set g.status = :completedStatus, g.completedAt = :now, g.version = g.version + 1 "
            + "where g.id = :id and g.status = :activeStatus "
            + "and g.currentAmountMinor >= g.targetAmountMinor")
    int markCompletedIfReached(@Param("id") Long id,
            @Param("activeStatus") GoalStatus activeStatus,
            @Param("completedStatus") GoalStatus completedStatus,
            @Param("now") Instant now);

    /** Nightly repair: writes the recomputed sum only when it differs (C.3 #16 invariant). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Goal g set g.currentAmountMinor = :value, g.version = g.version + 1 "
            + "where g.id = :id and g.currentAmountMinor <> :value")
    int repairCurrent(@Param("id") Long id, @Param("value") long value);
}
