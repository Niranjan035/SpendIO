package com.spendio.goals.repository;

import com.spendio.goals.domain.GoalContribution;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoalContributionRepository extends JpaRepository<GoalContribution, Long> {

    Optional<GoalContribution> findByIdAndGoalIdAndUserId(Long id, Long goalId, Long userId);

    Optional<GoalContribution> findByTransactionId(Long transactionId);

    Page<GoalContribution> findByGoalIdOrderByContributedOnDescIdDesc(Long goalId, Pageable pageable);

    /** Full history for the chart and milestone timeline (bounded by contribution count). */
    List<GoalContribution> findByGoalIdOrderByContributedOnAscIdAsc(Long goalId);

    long countByGoalId(Long goalId);

    @Query("select coalesce(sum(c.amountMinor), 0) from GoalContribution c where c.goalId = :goalId")
    long sumByGoalId(@Param("goalId") Long goalId);
}
