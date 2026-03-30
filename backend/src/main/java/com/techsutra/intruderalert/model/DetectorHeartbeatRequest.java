package com.techsutra.intruderalert.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class DetectorHeartbeatRequest {
    @NotBlank
    private String cameraId;
    @NotBlank
    private String timestamp;
    @NotNull
    private Integer alertCooldownSeconds;

    public String getCameraId() {
        return cameraId;
    }

    public void setCameraId(String cameraId) {
        this.cameraId = cameraId;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public Integer getAlertCooldownSeconds() {
        return alertCooldownSeconds;
    }

    public void setAlertCooldownSeconds(Integer alertCooldownSeconds) {
        this.alertCooldownSeconds = alertCooldownSeconds;
    }
}
