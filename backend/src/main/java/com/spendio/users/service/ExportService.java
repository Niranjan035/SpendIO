package com.spendio.users.service;

import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.sharedkernel.export.DataExporter;
import com.spendio.users.domain.User;
import com.spendio.users.dto.response.UserDataExportResponse;
import com.spendio.users.dto.response.UserResponse;
import com.spendio.users.repository.UserRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles a complete JSON export of the caller's data (ARCHITECTURE.md D.3, Phase 14).
 *
 * <p>Feature modules contribute their own sections through the kernel
 * {@link DataExporter} port, so this service never depends on a feature module (B.3)
 * and a module added later is included without touching this class.
 */
@Service
public class ExportService {

    private final UserRepository userRepository;
    private final PreferenceService preferenceService;
    private final List<DataExporter> exporters;

    public ExportService(UserRepository userRepository, PreferenceService preferenceService,
            List<DataExporter> exporters) {
        this.userRepository = userRepository;
        this.preferenceService = preferenceService;
        this.exporters = exporters;
    }

    @Transactional(readOnly = true)
    public UserDataExportResponse export(Long userId) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "User not found"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("profile", UserResponse.from(user));
        data.put("preferences", preferenceService.get(userId));
        for (DataExporter exporter : exporters) {
            data.put(exporter.section(), exporter.exportFor(userId));
        }
        return new UserDataExportResponse(Instant.now(), data);
    }
}
