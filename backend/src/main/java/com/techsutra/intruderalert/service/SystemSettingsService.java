package com.techsutra.intruderalert.service;

import com.techsutra.intruderalert.config.NotificationProperties;
import com.techsutra.intruderalert.entity.DetectorStatusEntity;
import com.techsutra.intruderalert.entity.SystemSettingsEntity;
import com.techsutra.intruderalert.model.DetectorHeartbeatRequest;
import com.techsutra.intruderalert.model.SystemSettingsResponse;
import com.techsutra.intruderalert.model.SystemSettingsUpdateRequest;
import com.techsutra.intruderalert.repository.DetectorStatusRepository;
import com.techsutra.intruderalert.repository.SystemSettingsRepository;
import com.techsutra.intruderalert.repository.UserAccountRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class SystemSettingsService {
    private static final long SETTINGS_ID = 1L;
    private static final long DETECTOR_ONLINE_THRESHOLD_SECONDS = 45L;

    private final SystemSettingsRepository systemSettingsRepository;
    private final DetectorStatusRepository detectorStatusRepository;
    private final UserAccountRepository userAccountRepository;
    private final NotificationProperties notificationProperties;

    public SystemSettingsService(
            SystemSettingsRepository systemSettingsRepository,
            DetectorStatusRepository detectorStatusRepository,
            UserAccountRepository userAccountRepository,
            NotificationProperties notificationProperties
    ) {
        this.systemSettingsRepository = systemSettingsRepository;
        this.detectorStatusRepository = detectorStatusRepository;
        this.userAccountRepository = userAccountRepository;
        this.notificationProperties = notificationProperties;
    }

    public SystemSettingsResponse getSettings() {
        SystemSettingsEntity settings = getOrCreateSettings();
        DetectorStatusEntity detectorStatus = detectorStatusRepository.findTopByOrderByIdDesc().orElse(null);

        SystemSettingsResponse response = new SystemSettingsResponse();
        response.setEmailEnabled(settings.isEmailEnabled());
        response.setDashboardBaseUrl(settings.getDashboardBaseUrl());
        response.setFromEmail(notificationProperties.getFromEmail());
        response.setRegisteredRecipients((int) userAccountRepository.count());

        if (detectorStatus != null) {
            response.setDetectorCameraId(detectorStatus.getCameraId());
            response.setLastHeartbeatAt(detectorStatus.getLastHeartbeatAt());
            response.setDetectorAlertCooldownSeconds(detectorStatus.getAlertCooldownSeconds());
            response.setDetectorOnline(isDetectorOnline(detectorStatus.getLastHeartbeatAt()));
        } else {
            response.setDetectorOnline(false);
        }

        return response;
    }

    public SystemSettingsResponse updateSettings(SystemSettingsUpdateRequest request) {
        SystemSettingsEntity settings = getOrCreateSettings();
        settings.setEmailEnabled(request.isEmailEnabled());
        settings.setDashboardBaseUrl(request.getDashboardBaseUrl().trim());
        systemSettingsRepository.save(settings);
        return getSettings();
    }

    public void recordDetectorHeartbeat(DetectorHeartbeatRequest request) {
        DetectorStatusEntity detectorStatus = detectorStatusRepository.findTopByOrderByIdDesc()
                .orElseGet(DetectorStatusEntity::new);
        detectorStatus.setCameraId(request.getCameraId().trim());
        detectorStatus.setLastHeartbeatAt(request.getTimestamp());
        detectorStatus.setAlertCooldownSeconds(request.getAlertCooldownSeconds());
        detectorStatusRepository.save(detectorStatus);
    }

    public boolean isEmailEnabled() {
        return getOrCreateSettings().isEmailEnabled();
    }

    public String getDashboardBaseUrl() {
        return getOrCreateSettings().getDashboardBaseUrl();
    }

    private SystemSettingsEntity getOrCreateSettings() {
        return systemSettingsRepository.findById(SETTINGS_ID).orElseGet(() -> {
            SystemSettingsEntity settings = new SystemSettingsEntity();
            settings.setId(SETTINGS_ID);
            settings.setEmailEnabled(notificationProperties.isEmailEnabled());
            settings.setDashboardBaseUrl(notificationProperties.getDashboardBaseUrl());
            return systemSettingsRepository.save(settings);
        });
    }

    private boolean isDetectorOnline(String timestamp) {
        try {
            Instant lastHeartbeat = Instant.parse(timestamp);
            return Duration.between(lastHeartbeat, Instant.now()).getSeconds() <= DETECTOR_ONLINE_THRESHOLD_SECONDS;
        } catch (Exception exception) {
            return false;
        }
    }
}
