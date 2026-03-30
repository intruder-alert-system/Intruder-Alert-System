package com.techsutra.intruderalert.model;

public class SystemSettingsResponse {
    private boolean emailEnabled;
    private String dashboardBaseUrl;
    private String fromEmail;
    private int registeredRecipients;
    private boolean detectorOnline;
    private String detectorCameraId;
    private String lastHeartbeatAt;
    private Integer detectorAlertCooldownSeconds;

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

    public String getFromEmail() {
        return fromEmail;
    }

    public void setFromEmail(String fromEmail) {
        this.fromEmail = fromEmail;
    }

    public int getRegisteredRecipients() {
        return registeredRecipients;
    }

    public void setRegisteredRecipients(int registeredRecipients) {
        this.registeredRecipients = registeredRecipients;
    }

    public boolean isDetectorOnline() {
        return detectorOnline;
    }

    public void setDetectorOnline(boolean detectorOnline) {
        this.detectorOnline = detectorOnline;
    }

    public String getDetectorCameraId() {
        return detectorCameraId;
    }

    public void setDetectorCameraId(String detectorCameraId) {
        this.detectorCameraId = detectorCameraId;
    }

    public String getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public void setLastHeartbeatAt(String lastHeartbeatAt) {
        this.lastHeartbeatAt = lastHeartbeatAt;
    }

    public Integer getDetectorAlertCooldownSeconds() {
        return detectorAlertCooldownSeconds;
    }

    public void setDetectorAlertCooldownSeconds(Integer detectorAlertCooldownSeconds) {
        this.detectorAlertCooldownSeconds = detectorAlertCooldownSeconds;
    }
}
