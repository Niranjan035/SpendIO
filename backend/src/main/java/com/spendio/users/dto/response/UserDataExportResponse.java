package com.spendio.users.dto.response;

import java.time.Instant;
import java.util.Map;

/**
 * The downloadable data export (ARCHITECTURE.md E.3, Phase 14): an envelope plus one
 * entry per contributing module. Built synchronously from the same read models the
 * screens use, so it is always consistent with what the user sees.
 */
public record UserDataExportResponse(Instant exportedAt, Map<String, Object> data) {
}
