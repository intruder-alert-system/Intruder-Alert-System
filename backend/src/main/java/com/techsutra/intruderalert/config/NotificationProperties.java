package com.techsutra.intruderalert.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.notifications")
public class NotificationProperties {
    private boolean emailEnabled = false;
    private String fromEmail = "";
    private String dashboardBaseUrl = "http://localhost:5173";

    public boolean isEmailEnabled() {
        return emailEnabled;
    }

    public void setEmailEnabled(boolean emailEnabled) {
        this.emailEnabled = emailEnabled;
    }

    public String getFromEmail() {
        return fromEmail;
    }

    public void setFromEmail(String fromEmail) {
        this.fromEmail = fromEmail;
    }

    public String getDashboardBaseUrl() {
        return dashboardBaseUrl;
    }

    public void setDashboardBaseUrl(String dashboardBaseUrl) {
        this.dashboardBaseUrl = dashboardBaseUrl;
    }
}
