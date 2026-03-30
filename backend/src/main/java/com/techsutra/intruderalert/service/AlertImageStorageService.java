package com.techsutra.intruderalert.service;

import com.techsutra.intruderalert.entity.AlertEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

@Service
public class AlertImageStorageService {
    private final Path alertImagesDirectory = Paths.get("data", "alert-images");

    public String storeSnapshot(String fileName, String imageBase64) {
        try {
            Files.createDirectories(alertImagesDirectory);
            Path imagePath = alertImagesDirectory.resolve(fileName);
            Files.write(imagePath, Base64.getDecoder().decode(imageBase64));
            return imagePath.toString();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not store alert snapshot.", exception);
        }
    }

    public byte[] loadSnapshot(AlertEntity alertEntity) {
        try {
            if (alertEntity.getImagePath() != null && !alertEntity.getImagePath().isBlank()) {
                return Files.readAllBytes(Path.of(alertEntity.getImagePath()));
            }

            if (alertEntity.getImageBase64() != null && !alertEntity.getImageBase64().isBlank()) {
                return Base64.getDecoder().decode(alertEntity.getImageBase64());
            }

            throw new IllegalStateException("Alert snapshot is not available.");
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load alert snapshot.", exception);
        }
    }
}
