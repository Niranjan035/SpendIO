package com.spendio.imports.listener;

import com.spendio.imports.repository.CsvImportRepository;
import com.spendio.sharedkernel.events.UserAccountDeletedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Purges every stored import (and its parsed rows) when an account is deleted
 * (ARCHITECTURE.md D.3, D.12, Phase 14). Reacts to the kernel event so {@code users}
 * never depends on {@code imports}.
 */
@Component
public class UserDeletionImportListener {

    private final CsvImportRepository imports;

    public UserDeletionImportListener(CsvImportRepository imports) {
        this.imports = imports;
    }

    @EventListener
    @Transactional
    public void on(UserAccountDeletedEvent event) {
        imports.deleteByUserId(event.userId());
    }
}
