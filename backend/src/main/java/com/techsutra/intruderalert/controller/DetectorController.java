package com.techsutra.intruderalert.controller;

import com.techsutra.intruderalert.config.AppSecurityProperties;
import com.techsutra.intruderalert.model.DetectorAuthorizedFaceRecord;
import com.techsutra.intruderalert.model.DetectorHeartbeatRequest;
import com.techsutra.intruderalert.service.AuthorizedFaceService;
import com.techsutra.intruderalert.service.SystemSettingsService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/detector")
public class DetectorController {
    private final AppSecurityProperties appSecurityProperties;
    private final SystemSettingsService systemSettingsService;
    private final AuthorizedFaceService authorizedFaceService;

    public DetectorController(
            AppSecurityProperties appSecurityProperties,
            SystemSettingsService systemSettingsService,
            AuthorizedFaceService authorizedFaceService
    ) {
        this.appSecurityProperties = appSecurityProperties;
        this.systemSettingsService = systemSettingsService;
        this.authorizedFaceService = authorizedFaceService;
    }

    @PostMapping("/heartbeat")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> heartbeat(
            @Valid @RequestBody DetectorHeartbeatRequest request,
            @RequestHeader(value = "X-Detector-Key", required = false) String detectorKey
    ) {
        validateDetectorKey(detectorKey);

        systemSettingsService.recordDetectorHeartbeat(request);
        return Map.of("status", "recorded");
    }

    @GetMapping("/faces")
    public java.util.List<DetectorAuthorizedFaceRecord> faces(
            @RequestHeader(value = "X-Detector-Key", required = false) String detectorKey
    ) {
        validateDetectorKey(detectorKey);
        return authorizedFaceService.getDetectorFaces();
    }

    @GetMapping("/faces/{id}/image")
    public ResponseEntity<byte[]> faceImage(
            @PathVariable Long id,
            @RequestHeader(value = "X-Detector-Key", required = false) String detectorKey
    ) {
        validateDetectorKey(detectorKey);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body(authorizedFaceService.getDetectorFaceImage(id));
    }

    private void validateDetectorKey(String detectorKey) {
        if (!appSecurityProperties.getDetectorApiKey().equals(detectorKey)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid detector key.");
        }
    }
}
