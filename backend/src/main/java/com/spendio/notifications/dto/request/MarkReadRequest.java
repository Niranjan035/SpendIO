package com.spendio.notifications.dto.request;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** PATCH /notifications/read-all body - the ids created since the last poll, if any. */
public record MarkReadRequest(@NotEmpty List<Long> ids) {
}