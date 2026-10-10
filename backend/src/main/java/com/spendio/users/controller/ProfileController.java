package com.spendio.users.controller;

import com.spendio.users.dto.request.ChangeBaseCurrencyRequest;
import com.spendio.users.dto.request.DeleteAccountRequest;
import com.spendio.users.dto.request.UpdateProfileRequest;
import com.spendio.users.dto.response.UserDataExportResponse;
import com.spendio.users.dto.response.UserResponse;
import com.spendio.users.service.ExportService;
import com.spendio.users.service.ProfileService;
import com.spendio.users.service.UserDeletionService;
import com.spendio.sharedkernel.web.CurrentUserId;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Profile endpoints for the authenticated account (ARCHITECTURE.md E.3). */
@RestController
@RequestMapping("/api/v1/users/me")
public class ProfileController {

    private final ProfileService profileService;
    private final ExportService exportService;
    private final UserDeletionService userDeletionService;

    public ProfileController(ProfileService profileService, ExportService exportService,
            UserDeletionService userDeletionService) {
        this.profileService = profileService;
        this.exportService = exportService;
        this.userDeletionService = userDeletionService;
    }

    @GetMapping
    public UserResponse get(@CurrentUserId Long userId) {
        return profileService.get(userId);
    }

    @PatchMapping
    public UserResponse update(@CurrentUserId Long userId, @Valid @RequestBody UpdateProfileRequest request) {
        return profileService.update(userId, request);
    }

    @PutMapping("/base-currency")
    @ResponseStatus(HttpStatus.OK)
    public UserResponse changeBaseCurrency(
            @CurrentUserId Long userId, @Valid @RequestBody ChangeBaseCurrencyRequest request) {
        return profileService.changeBaseCurrency(userId, request);
    }

    /** Full JSON export of everything the caller owns (ARCHITECTURE.md E.3, Phase 14). */
    @PostMapping("/export")
    @ResponseStatus(HttpStatus.OK)
    public UserDataExportResponse export(@CurrentUserId Long userId) {
        return exportService.export(userId);
    }

    /** Deletes the account after password + typed confirmation (ARCHITECTURE.md E.3). */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUserId Long userId, @Valid @RequestBody DeleteAccountRequest request) {
        userDeletionService.delete(userId, request);
    }
}
