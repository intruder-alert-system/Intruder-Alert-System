package com.techsutra.intruderalert.model;

public class UserProfile {
    private Long id;
    private String fullName;
    private String email;
    private String activeDeviceName;

    public UserProfile() {
    }

    public UserProfile(Long id, String fullName, String email, String activeDeviceName) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.activeDeviceName = activeDeviceName;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getActiveDeviceName() {
        return activeDeviceName;
    }

    public void setActiveDeviceName(String activeDeviceName) {
        this.activeDeviceName = activeDeviceName;
    }
}
