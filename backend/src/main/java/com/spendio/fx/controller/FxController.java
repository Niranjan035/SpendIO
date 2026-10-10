package com.spendio.fx.controller;

import com.spendio.fx.dto.request.ConvertRequest;
import com.spendio.fx.dto.request.ManualRateRequest;
import com.spendio.fx.dto.response.ConvertResponse;
import com.spendio.fx.dto.response.FxRateResponse;
import com.spendio.fx.job.FxRateRefreshJob;
import com.spendio.fx.service.FxRateService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FX endpoints (ARCHITECTURE.md D.5): rate history, manual override, conversion and a
 * user-triggered refresh of the daily job.
 */
@RestController
@RequestMapping("/api/v1/fx")
public class FxController {

    private final FxRateService fxRateService;

    public FxController(FxRateService fxRateService) {
        this.fxRateService = fxRateService;
    }

    /** Rate history for a pair, optionally bounded by date; newest first. */
    @GetMapping("/rates")
    public List<FxRateResponse> rates(
            @RequestParam String base,
            @RequestParam String quote,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return fxRateService.history(base, quote, from, to);
    }

    /** Resolved current rate for a pair through the documented fallback chain. */
    @GetMapping("/rates/current")
    public FxRateResponse current(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return fxRateService.findRate(from, to, date == null ? LocalDate.now() : date);
    }

    /** Manual override - stored under the {@code manual} provider, preferred on read. */
    @PostMapping("/rates")
    public FxRateResponse overrideRate(@Valid @RequestBody ManualRateRequest request) {
        return fxRateService.saveManualRate(request);
    }

    /** Convert an amount in minor units at the resolved rate. */
    @PostMapping("/convert")
    public ConvertResponse convert(@Valid @RequestBody ConvertRequest request) {
        return fxRateService.convert(request.amountMinor(), request.fromCurrency(),
                request.toCurrency(), request.date());
    }

    /** Run the daily refresh now (user-triggered; ARCHITECTURE.md D.5). */
    @PostMapping("/refresh")
    public ResponseEntity<FxRateRefreshJob.RefreshSummary> refresh() {
        return ResponseEntity.ok(fxRateService.refresh());
    }
}