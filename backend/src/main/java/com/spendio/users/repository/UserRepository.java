package com.spendio.users.repository;

import com.spendio.users.domain.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Profile persistence (ARCHITECTURE.md D.3). Soft-deleted accounts are never returned
 * by the read paths, so the address can be reused while the row stays for audit.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("select u from User u where u.id = :id and u.deletedAt is null")
    Optional<User> findByIdAndDeletedAtIsNull(@Param("id") Long id);

    @Query("select u from User u where u.email = :email and u.deletedAt is null")
    Optional<User> findByEmailAndDeletedAtIsNull(@Param("email") String email);

    /** Hard-purge candidates: rows soft-deleted before the grace cutoff (Phase 14). */
    @Query("select u.id from User u where u.deletedAt is not null and u.deletedAt < :cutoff")
    List<Long> findIdsByDeletedAtBefore(@Param("cutoff") Instant cutoff);
}
