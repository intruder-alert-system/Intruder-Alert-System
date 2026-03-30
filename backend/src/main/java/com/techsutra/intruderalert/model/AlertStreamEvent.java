package com.techsutra.intruderalert.model;

public class AlertStreamEvent {
    private String type;
    private AlertRecord alert;

    public AlertStreamEvent() {
    }

    public AlertStreamEvent(String type, AlertRecord alert) {
        this.type = type;
        this.alert = alert;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public AlertRecord getAlert() {
        return alert;
    }

    public void setAlert(AlertRecord alert) {
        this.alert = alert;
    }
}
