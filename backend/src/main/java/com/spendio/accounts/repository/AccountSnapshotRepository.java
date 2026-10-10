package com.spendio.accounts.repository;

import com.spendio.accounts.domain.AccountSnapshot;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Snapshot reads/writes for the daily net-worth rollup (ARCHITECTURE.md C.3 #11).
 */
public interface AccountSnapshotRepository extends JpaRepository<AccountSnapshot, Long> {

    Optional<AccountSnapshot> findByAccountIdAndSnapshotDate(Long accountId, LocalDate snapshotDate);

    List<AccountSnapshot> findByUserIdAndSnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(
            Long userId, LocalDate from);

    long countByUserIdAndSnapshotDate(Long userId, LocalDate snapshotDate);

    @Query(value = "SELECT DISTINCT user_id FROM accounts WHERE deleted_at IS NULL", nativeQuery = true)
    List<Long> findUserIdsWithActiveAccounts();
}
