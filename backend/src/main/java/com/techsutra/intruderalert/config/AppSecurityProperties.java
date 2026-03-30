package com.techsutra.intruderalert.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public class AppSecurityProperties {
    private long tokenValidityHours = 168;
    private String detectorApiKey = "change-me-detector-key";

    public long getTokenValidityHours() {
        return tokenValidityHours;
    }

    public void setTokenValidityHours(long tokenValidityHours) {
        this.tokenValidityHours = tokenValidityHours;
    }

    public String getDetectorApiKey() {
        return detectorApiKey;
    }

    public void setDetectorApiKey(String detectorApiKey) {
        this.detectorApiKey = detectorApiKey;
    }
}
