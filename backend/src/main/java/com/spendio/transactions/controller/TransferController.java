package com.spendio.transactions.controller;

import com.spendio.sharedkernel.web.CurrentUserId;
import com.spendio.sharedkernel.web.PageResponse;
import com.spendio.transactions.dto.request.CreateTransferRequest;
import com.spendio.transactions.dto.request.ReverseTransactionRequest;
import com.spendio.transactions.dto.response.TransferResponse;
import com.spendio.transactions.service.TransactionReversalService;
import com.spendio.transactions.service.TransferService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Transfer endpoints (ARCHITECTURE.md E.8): create a linked pair, list groups,
 * fetch both legs, and reverse the whole group atomically.
 */
@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final TransferService service;
    private final TransactionReversalService reversal;

    public TransferController(TransferService service, TransactionReversalService reversal) {
        this.service = service;
        this.reversal = reversal;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse create(@CurrentUserId Long userId, @Valid @RequestBody CreateTransferRequest request) {
        return service.create(userId, request);
    }

    @GetMapping
    public PageResponse<TransferResponse> list(
            @CurrentUserId Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return service.list(userId, from, to, accountId, page, size);
    }

    @GetMapping("/{transferGroupId}")
    public TransferResponse detail(@CurrentUserId Long userId, @PathVariable String transferGroupId) {
        return service.detail(userId, transferGroupId);
    }

    @PostMapping("/{transferGroupId}/reverse")
    public TransferResponse reverse(@CurrentUserId Long userId, @PathVariable String transferGroupId,
            @Valid @RequestBody ReverseTransactionRequest request) {
        return reversal.reverseTransfer(userId, transferGroupId, request);
    }
}
