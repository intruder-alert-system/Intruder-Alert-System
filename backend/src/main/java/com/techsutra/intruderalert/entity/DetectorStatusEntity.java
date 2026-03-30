package com.techsutra.intruderalert.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "detector_status")
public class DetectorStatusEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String cameraId;
    private String lastHeartbeatAt;
    private Integer alertCooldownSeconds;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCameraId() {
        return cameraId;
    }

    public void setCameraId(String cameraId) {
        this.cameraId = cameraId;
    }

    public String getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public void setLastHeartbeatAt(String lastHeartbeatAt) {
        this.lastHeartbeatAt = lastHeartbeatAt;
    }

    public Integer getAlertCooldownSeconds() {
        return alertCooldownSeconds;
    }

    public void setAlertCooldownSeconds(Integer alertCooldownSeconds) {
        this.alertCooldownSeconds = alertCooldownSeconds;
    }
}
