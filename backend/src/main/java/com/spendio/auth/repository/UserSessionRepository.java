package com.spendio.auth.repository;

import com.spendio.auth.domain.RevocationReason;
import com.spendio.auth.domain.UserSession;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Session persistence (ARCHITECTURE.md D.2); all reads are user- or family-scoped. */
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    Optional<UserSession> findByRefreshTokenHash(String refreshTokenHash);

    Optional<UserSession> findBySessionUuid(String sessionUuid);

    List<UserSession> findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByIssuedAtDesc(
            Long userId, Instant now);

    long countByUserIdAndRevokedAtIsNullAndExpiresAtAfter(Long userId, Instant now);

    List<UserSession> findByFamilyId(String familyId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update UserSession s
               set s.revokedAt = :at, s.revocationReason = :reason
             where s.familyId = :familyId and s.revokedAt is null
            """)
    int revokeFamily(@Param("familyId") String familyId, @Param("reason") RevocationReason reason, @Param("at") Instant at);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update UserSession s
               set s.revokedAt = :at, s.revocationReason = :reason
             where s.userId = :userId and s.revokedAt is null
            """)
    int revokeAllForUser(
            @Param("userId") Long userId, @Param("reason") RevocationReason reason, @Param("at") Instant at);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update UserSession s
               set s.revokedAt = :at, s.revocationReason = :reason
             where s.userId = :userId and s.revokedAt is null and s.sessionUuid <> :keepSessionUuid
            """)
    int revokeAllForUserExcept(
            @Param("userId") Long userId,
            @Param("keepSessionUuid") String keepSessionUuid,
            @Param("reason") RevocationReason reason,
            @Param("at") Instant at);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from UserSession s where s.expiresAt < :before")
    int deleteExpired(@Param("before") Instant before);
}
