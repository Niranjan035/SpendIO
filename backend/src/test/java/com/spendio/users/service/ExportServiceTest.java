package com.spendio.users.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.sharedkernel.export.DataExporter;
import com.spendio.users.domain.User;
import com.spendio.users.dto.response.UserDataExportResponse;
import com.spendio.users.dto.response.UserPreferenceResponse;
import com.spendio.users.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Data export (ARCHITECTURE.md D.3, Phase 14): profile plus every module's section. */
@ExtendWith(MockitoExtension.class)
class ExportServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PreferenceService preferenceService;

    @Test
    void collectsProfilePreferencesAndEveryExporterSection() {
        User user = User.create("ana@example.com", "{bcrypt}", "UTC", "en-GB", "USD");
        when(userRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(user));
        when(preferenceService.get(7L)).thenReturn(preference());

        DataExporter accounts = exporter("accounts", List.of("a1"));
        DataExporter transactions = exporter("transactions", List.of("t1"));
        ExportService service = new ExportService(userRepository, preferenceService,
                List.of(accounts, transactions));

        UserDataExportResponse response = service.export(7L);

        assertThat(response.exportedAt()).isNotNull();
        assertThat(response.data()).containsKeys("profile", "preferences", "accounts", "transactions");
        assertThat(response.data().get("accounts")).isEqualTo(List.of("a1"));
    }

    @Test
    void rejectsUnknownUser() {
        when(userRepository.findByIdAndDeletedAtIsNull(404L)).thenReturn(Optional.empty());
        ExportService service = new ExportService(userRepository, preferenceService, List.of());

        assertThatThrownBy(() -> service.export(404L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    private static DataExporter exporter(String section, Object payload) {
        return new DataExporter() {
            @Override
            public String section() {
                return section;
            }

            @Override
            public Object exportFor(Long userId) {
                return payload;
            }
        };
    }

    private static UserPreferenceResponse preference() {
        return new UserPreferenceResponse(
                com.spendio.users.domain.Theme.SYSTEM, "yyyy-MM-dd", 1,
                com.spendio.users.domain.DecimalStyle.DOT, ",",
                com.spendio.users.domain.SymbolPosition.BEFORE, "#,##0.00",
                80, 30, 3, false, false);
    }
}
