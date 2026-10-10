package com.spendio.accounts.repository;

import com.spendio.accounts.domain.Account;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Account persistence (ARCHITECTURE.md D.6). The "at most one primary per user" rule
 * and the user-scoped reads below are enforced with derived queries; the primary
 * demotion is a single statement so it stays atomic.
 */
public interface AccountRepository extends JpaRepository<Account, Long>, JpaSpecificationExecutor<Account> {

    Optional<Account> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

    List<Account> findByUserIdAndDeletedAtIsNull(Long userId);

    boolean existsByUserIdAndNameAndDeletedAtIsNull(Long userId, String name);

    boolean existsByUserIdAndNameAndDeletedAtIsNullAndIdNot(Long userId, String name, Long excludeId);

    Optional<Account> findFirstByUserIdAndPrimaryAccountTrueAndDeletedAtIsNull(Long userId);

    @Modifying(clearAutomatically = true)
    @Query("update Account a set a.primaryAccount = false where a.userId = :userId and a.primaryAccount = true and a.id <> :keepId")
    int demoteAllOtherPrimaries(@Param("userId") Long userId, @Param("keepId") Long keepId);
}