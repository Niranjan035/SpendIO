package com.spendio.sharedkernel.export;

/**
 * Contributes one section to a user's data export (ARCHITECTURE.md D.3, Phase 14).
 *
 * <p>Each module implements this for its own tables and the implementation is
 * discovered as a Spring bean, so {@code ExportService} assembles a complete document
 * without depending on any feature module (B.3). Returned values must be JSON-friendly
 * (DTO records or maps), never JPA entities.
 */
public interface DataExporter {

    /** Stable section key in the export document, e.g. {@code accounts}. */
    String section();

    Object exportFor(Long userId);
}
