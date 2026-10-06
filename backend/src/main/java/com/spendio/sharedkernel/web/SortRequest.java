package com.spendio.sharedkernel.web;

import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import java.util.Collection;
import java.util.Locale;
import org.springframework.data.domain.Sort;

/**
 * Whitelist-validated {@code ?sort=field,desc} parsing (ARCHITECTURE.md E.1).
 * Anything outside the per-endpoint allowlist is a 400, never an implicit fallback.
 */
public record SortRequest(String property, Sort.Direction direction) {

    private static final int MAX_PARTS = 2;

    public static final SortRequest UNSORTED = new SortRequest(null, Sort.Direction.ASC);

    public static SortRequest parse(String raw, Collection<String> allowedProperties) {
        if (raw == null || raw.isBlank()) {
            return UNSORTED;
        }
        String[] parts = raw.split(",", MAX_PARTS);
        String property = parts[0].trim();
        Sort.Direction direction = Sort.Direction.ASC;
        if (parts.length == MAX_PARTS && !parts[1].isBlank()) {
            try {
                direction = Sort.Direction.fromString(parts[1].trim().toLowerCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw invalid(raw);
            }
        }
        if (!allowedProperties.contains(property)) {
            throw invalid(raw);
        }
        return new SortRequest(property, direction);
    }

    public Sort toSort() {
        return property == null ? Sort.unsorted() : Sort.by(direction, property);
    }

    private static BusinessException invalid(String raw) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, "Unsupported sort value: " + raw)
                .withFieldError("sort", "must be one of the sortable fields in direction asc|desc");
    }
}
