package com.spendio.auth.repository;

import com.spendio.auth.domain.PasswordResetToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Reset-token persistence (ARCHITECTURE.md D.2). */
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update PasswordResetToken t
               set t.usedAt = :at
             where t.userId = :userId and t.usedAt is null
            """)
    int invalidateAllForUser(@Param("userId") Long userId, @Param("at") Instant at);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from PasswordResetToken t where t.expiresAt < :before")
    int deleteExpired(@Param("before") Instant before);
}
