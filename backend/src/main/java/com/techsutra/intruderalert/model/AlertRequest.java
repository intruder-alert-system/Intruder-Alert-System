package com.techsutra.intruderalert.model;

import jakarta.validation.constraints.NotBlank;

public class AlertRequest {
    @NotBlank
    private String cameraId;
    @NotBlank
    private String timestamp;
    @NotBlank
    private String imageBase64;
    @NotBlank
    private String fileName;
    @NotBlank
    private String message;

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

    public String getImageBase64() {
        return imageBase64;
    }

    public void setImageBase64(String imageBase64) {
        this.imageBase64 = imageBase64;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
