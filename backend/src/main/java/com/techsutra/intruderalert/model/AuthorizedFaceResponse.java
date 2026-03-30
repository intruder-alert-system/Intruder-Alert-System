package com.techsutra.intruderalert.model;

public class AuthorizedFaceResponse {
    private Long id;
    private String displayName;
    private String updatedAt;
    private boolean enrolled;

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

    public boolean isEnrolled() {
        return enrolled;
    }

    public void setEnrolled(boolean enrolled) {
        this.enrolled = enrolled;
    }
}
