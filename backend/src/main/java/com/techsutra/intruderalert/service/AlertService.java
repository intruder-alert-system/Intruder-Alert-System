package com.techsutra.intruderalert.service;

import com.techsutra.intruderalert.config.AppSecurityProperties;
import com.techsutra.intruderalert.entity.AlertEntity;
import com.techsutra.intruderalert.model.AlertRecord;
import com.techsutra.intruderalert.model.AlertRequest;
import com.techsutra.intruderalert.repository.AlertRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class AlertService {
    private final AlertRepository alertRepository;
    private final AppSecurityProperties appSecurityProperties;
    private final EmailNotificationService emailNotificationService;
    private final AlertStreamService alertStreamService;
    private final AlertImageStorageService alertImageStorageService;

    public AlertService(
            AlertRepository alertRepository,
            AppSecurityProperties appSecurityProperties,
            EmailNotificationService emailNotificationService,
            AlertStreamService alertStreamService,
            AlertImageStorageService alertImageStorageService
    ) {
        this.alertRepository = alertRepository;
        this.appSecurityProperties = appSecurityProperties;
        this.emailNotificationService = emailNotificationService;
        this.alertStreamService = alertStreamService;
        this.alertImageStorageService = alertImageStorageService;
    }

    public AlertRecord createAlert(AlertRequest request, String detectorApiKey) {
        if (!appSecurityProperties.getDetectorApiKey().equals(detectorApiKey)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid detector key.");
        }

        AlertEntity alertEntity = new AlertEntity();
        alertEntity.setCameraId(request.getCameraId());
        alertEntity.setTimestamp(request.getTimestamp());
        alertEntity.setImagePath(
                alertImageStorageService.storeSnapshot(request.getFileName(), request.getImageBase64())
        );
        alertEntity.setImageBase64(null);
        alertEntity.setFileName(request.getFileName());
        alertEntity.setMessage(request.getMessage());
        alertEntity.setSeverity("HIGH");
        alertEntity.setStatus("NEW");

        AlertRecord savedAlert = toRecord(alertRepository.save(alertEntity));
        emailNotificationService.sendIntruderAlertEmail(
                savedAlert,
                alertImageStorageService.loadSnapshot(alertEntity)
        );
        alertStreamService.publish("created", savedAlert);
        return savedAlert;
    }

    public List<AlertRecord> getAllAlerts() {
        return alertRepository.findAll().stream()
                .sorted(Comparator.comparing(AlertEntity::getId).reversed())
                .map(this::toRecord)
                .toList();
    }

    public AlertRecord getLatestAlert() {
        return alertRepository.findAll().stream()
                .max(Comparator.comparing(AlertEntity::getId))
                .map(this::toRecord)
                .orElse(null);
    }

    public void clearAlerts() {
        alertRepository.deleteAllInBatch();
        alertStreamService.publish("cleared", null);
    }

    public byte[] getAlertImage(Long id) {
        AlertEntity alertEntity = alertRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert not found."));
        return alertImageStorageService.loadSnapshot(alertEntity);
    }

    public AlertRecord updateStatus(Long id, String status) {
        AlertEntity alertEntity = alertRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert not found."));

        String normalizedStatus = status.trim().toUpperCase(Locale.ROOT);
        if (!List.of("NEW", "ACKNOWLEDGED", "RESOLVED").contains(normalizedStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported alert status.");
        }

        alertEntity.setStatus(normalizedStatus);
        AlertRecord updatedAlert = toRecord(alertRepository.save(alertEntity));
        alertStreamService.publish("updated", updatedAlert);
        return updatedAlert;
    }

    private AlertRecord toRecord(AlertEntity alertEntity) {
        return new AlertRecord(
                alertEntity.getId(),
                alertEntity.getCameraId(),
                alertEntity.getTimestamp(),
                alertEntity.getImageBase64(),
                alertEntity.getFileName(),
                alertEntity.getMessage(),
                alertEntity.getSeverity(),
                alertEntity.getStatus()
        );
    }
}
