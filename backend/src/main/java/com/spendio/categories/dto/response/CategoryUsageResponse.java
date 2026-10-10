package com.spendio.categories.dto.response;

import java.time.LocalDate;

public record CategoryUsageResponse(
    Long categoryId,
    LocalDate from,
    LocalDate to,
    long count,
    long totalMinor,
    java.util.List<Month> byMonth) {
  public record Month(String month, long totalMinor, long count) { }
}