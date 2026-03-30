package com.techsutra.intruderalert.controller;

import com.techsutra.intruderalert.model.SystemSettingsResponse;
import com.techsutra.intruderalert.model.SystemSettingsUpdateRequest;
import com.techsutra.intruderalert.service.SystemSettingsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings/system")
public class SettingsController {
    private final SystemSettingsService systemSettingsService;

    public SettingsController(SystemSettingsService systemSettingsService) {
        this.systemSettingsService = systemSettingsService;
    }

    @GetMapping
    public SystemSettingsResponse getSettings() {
        return systemSettingsService.getSettings();
    }

    @PatchMapping
    public SystemSettingsResponse updateSettings(@Valid @RequestBody SystemSettingsUpdateRequest request) {
        return systemSettingsService.updateSettings(request);
    }
}
