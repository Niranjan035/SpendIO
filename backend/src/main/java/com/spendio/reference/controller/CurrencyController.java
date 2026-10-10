package com.spendio.reference.controller;

import com.spendio.reference.dto.CurrencyResponse;
import com.spendio.reference.service.CurrencyService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public currency endpoints (ARCHITECTURE.md E.3) - the login and register screens
 * need the list before anyone is authenticated.
 */
@RestController
@RequestMapping("/api/v1/currencies")
public class CurrencyController {

    private final CurrencyService currencyService;

    public CurrencyController(CurrencyService currencyService) {
        this.currencyService = currencyService;
    }

    @GetMapping
    public List<CurrencyResponse> listActive() {
        return currencyService.listActive();
    }

    @GetMapping("/{code}")
    public CurrencyResponse get(@PathVariable String code) {
        return currencyService.get(code);
    }
}
