package com.spendio.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spendio.audit.domain.AuditLog;
import com.spendio.audit.repository.AuditLogRepository;
import com.spendio.sharedkernel.domain.enums.AuditActorType;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Audit writer (ARCHITECTURE.md E.3, Phase 14): presence and no-secret payloads. */
@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository repository;

    @InjectMocks
    private AuditLogService service;

    @Test
    void persistsEachRecordedEntry() {
        service.record(com.spendio.sharedkernel.audit.AuditEntry.user(7L, "SESSION_REVOKED", "SESSION", 3L));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        AuditLog row = captor.getValue();
        assertThat(row.getAction()).isEqualTo("SESSION_REVOKED");
        assertThat(row.getActorType()).isEqualTo(AuditActorType.USER);
        assertThat(row.getEntityType()).isEqualTo("SESSION");
        assertThat(row.getEntityId()).isEqualTo(3L);
        assertThat(row.getCreatedAt()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void listsTheCallersRowsPage() {
        when(repository.findByUserIdOrderByCreatedAtDesc(any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        assertThat(service.list(7L, null, null).content()).isEmpty();
    }
}
