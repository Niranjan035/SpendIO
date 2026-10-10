package com.spendio.auth.repository;

import com.spendio.auth.domain.TokenPurpose;
import com.spendio.auth.domain.VerificationToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Verification-token persistence (ARCHITECTURE.md D.2). */
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {

    Optional<VerificationToken> findByTokenHash(String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update VerificationToken t
               set t.consumedAt = :at
             where t.userId = :userId and t.purpose = :purpose and t.consumedAt is null
            """)
    int consumeAllForUser(
            @Param("userId") Long userId, @Param("purpose") TokenPurpose purpose, @Param("at") Instant at);

    long countByUserIdAndPurposeAndCreatedAtAfter(Long userId, TokenPurpose purpose, Instant after);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from VerificationToken t where t.expiresAt < :before")
    int deleteExpired(@Param("before") Instant before);
}
