package com.techsutra.intruderalert.model;

import jakarta.validation.constraints.NotBlank;

public class SystemSettingsUpdateRequest {
    private boolean emailEnabled;
    @NotBlank
    private String dashboardBaseUrl;

    public boolean isEmailEnabled() {
        return emailEnabled;
    }

    public void setEmailEnabled(boolean emailEnabled) {
        this.emailEnabled = emailEnabled;
    }

    public String getDashboardBaseUrl() {
        return dashboardBaseUrl;
    }

    public void setDashboardBaseUrl(String dashboardBaseUrl) {
        this.dashboardBaseUrl = dashboardBaseUrl;
    }
}
