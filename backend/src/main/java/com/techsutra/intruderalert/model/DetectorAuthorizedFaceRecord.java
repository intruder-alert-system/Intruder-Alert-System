package com.techsutra.intruderalert.model;

public class DetectorAuthorizedFaceRecord {
    private Long id;
    private String displayName;
    private String updatedAt;

    public DetectorAuthorizedFaceRecord() {
    }

    public DetectorAuthorizedFaceRecord(Long id, String displayName, String updatedAt) {
        this.id = id;
        this.displayName = displayName;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
