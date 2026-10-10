package com.spendio.imports.repository;

import com.spendio.imports.domain.CsvImport;
import com.spendio.sharedkernel.domain.enums.ImportStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Import job store (ARCHITECTURE.md D.12). */
public interface CsvImportRepository extends JpaRepository<CsvImport, Long> {

    Optional<CsvImport> findByIdAndUserId(Long id, Long userId);

    Page<CsvImport> findByUserId(Long userId, Pageable pageable);

    Page<CsvImport> findByUserIdAndStatus(Long userId, ImportStatus status, Pageable pageable);

    @Query("select i from CsvImport i where i.userId=:userId and i.status in ('UPLOADED','PARSING','PARSED','MAPPING_REQUIRED','VALIDATING','READY','PROCESSING') order by i.createdAt asc")
    List<CsvImport> findActiveForUser(@Param("userId") Long userId);

    /** Purges every uploaded file's rows on account deletion (Phase 14); rows cascade. */
    @Modifying
    @Query("delete from CsvImport i where i.userId = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}