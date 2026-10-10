package com.spendio.users.controller;

import com.spendio.users.dto.request.UpdatePreferencesRequest;
import com.spendio.users.dto.response.UserPreferenceResponse;
import com.spendio.users.service.PreferenceService;
import com.spendio.sharedkernel.web.CurrentUserId;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Preference endpoints for the authenticated account (ARCHITECTURE.md E.3). */
@RestController
@RequestMapping("/api/v1/users/me/preferences")
public class PreferenceController {

    private final PreferenceService preferenceService;

    public PreferenceController(PreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @GetMapping
    public UserPreferenceResponse get(@CurrentUserId Long userId) {
        return preferenceService.get(userId);
    }

    @PutMapping
    public UserPreferenceResponse replace(@CurrentUserId Long userId,
            @Valid @RequestBody UpdatePreferencesRequest request) {
        return preferenceService.update(userId, request);
    }
}
