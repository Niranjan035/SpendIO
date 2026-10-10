package com.spendio.imports.repository;

import com.spendio.imports.domain.CsvImportRow;
import com.spendio.sharedkernel.domain.enums.ImportRowStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Per-row store for an import (ARCHITECTURE.md D.12). */
public interface CsvImportRowRepository extends JpaRepository<CsvImportRow, Long> {

    List<CsvImportRow> findByImportIdOrderByRowNumber(Long importId);

    List<CsvImportRow> findByImportIdAndStatusIn(Long importId, Collection<ImportRowStatus> statuses);

    List<CsvImportRow> findByImportIdAndStatusInAndIdIn(
            Long importId, Collection<ImportRowStatus> statuses, Collection<Long> ids);

    List<CsvImportRow> findByImportIdAndStatus(Long importId, ImportRowStatus status);

    Page<CsvImportRow> findByImportIdAndStatus(Long importId, ImportRowStatus status, Pageable pageable);

    Page<CsvImportRow> findByImportId(Long importId, Pageable pageable);

    Optional<CsvImportRow> findByIdAndImportId(Long id, Long importId);

    long countByImportId(Long importId);

    @Query("select r from CsvImportRow r where r.importId=:importId and r.status in :statuses " +
            "and (r.duplicateOfRowId is not null or r.duplicateOfTransactionId is not null)")
    List<CsvImportRow> findDuplicates(@Param("importId") Long importId,
            @Param("statuses") Collection<ImportRowStatus> statuses);
}