package com.spendio.fx.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Outcome of a rate lookup (ARCHITECTURE.md D.5): the number to multiply by, which row
 * it came from (null when computed from an inverse or a triangulation), and whether the
 * rate is older than the requested date (stale fallback).
 */
public record ResolvedRate(BigDecimal rate, Long rateId, LocalDate rateDate, boolean stale, String source) {

    public static ResolvedRate of(BigDecimal rate, Long rateId, LocalDate rateDate, String source) {
        return new ResolvedRate(rate, rateId, rateDate, false, source);
    }

    public static ResolvedRate stale(BigDecimal rate, Long rateId, LocalDate rateDate, String source) {
        return new ResolvedRate(rate, rateId, rateDate, true, source);
    }
}