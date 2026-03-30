package com.techsutra.intruderalert.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "system_settings")
public class SystemSettingsEntity {
    @Id
    private Long id;

    private boolean emailEnabled;

    private String dashboardBaseUrl;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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
