package com.techsutra.intruderalert.controller;

import com.techsutra.intruderalert.model.AlertRecord;
import com.techsutra.intruderalert.model.AlertRequest;
import com.techsutra.intruderalert.model.AlertStatusUpdateRequest;
import com.techsutra.intruderalert.service.AlertService;
import com.techsutra.intruderalert.service.AlertStreamService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/alerts")
@CrossOrigin(origins = {"http://localhost:5173", "https://localhost:5173"})
public class AlertController {
    private final AlertService alertService;
    private final AlertStreamService alertStreamService;

    public AlertController(AlertService alertService, AlertStreamService alertStreamService) {
        this.alertService = alertService;
        this.alertStreamService = alertStreamService;
    }

    @GetMapping
    public List<AlertRecord> getAlerts() {
        return alertService.getAllAlerts();
    }

    @GetMapping("/latest")
    public AlertRecord getLatestAlert() {
        return alertService.getLatestAlert();
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> getAlertImage(@PathVariable Long id) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body(alertService.getAlertImage(id));
    }

    @GetMapping("/stream")
    public SseEmitter streamAlerts() {
        return alertStreamService.subscribe();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AlertRecord createAlert(
            @Valid @RequestBody AlertRequest request,
            @RequestHeader(value = "X-Detector-Key", required = false) String detectorKey
    ) {
        return alertService.createAlert(request, detectorKey);
    }

    @PatchMapping("/{id}/status")
    public AlertRecord updateAlertStatus(
            @PathVariable Long id,
            @Valid @RequestBody AlertStatusUpdateRequest request
    ) {
        return alertService.updateStatus(id, request.getStatus());
    }

    @DeleteMapping
    public Map<String, String> clearAlerts() {
        alertService.clearAlerts();
        return Map.of("status", "cleared");
    }
}
