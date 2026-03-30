package com.techsutra.intruderalert.service;

import com.techsutra.intruderalert.entity.AuthorizedFaceEntity;
import com.techsutra.intruderalert.entity.UserAccount;
import com.techsutra.intruderalert.model.AuthorizedFaceResponse;
import com.techsutra.intruderalert.model.DetectorAuthorizedFaceRecord;
import com.techsutra.intruderalert.repository.AuthorizedFaceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class AuthorizedFaceService {
    private static final long MAX_UPLOAD_BYTES = 2 * 1024 * 1024;

    private final AuthorizedFaceRepository authorizedFaceRepository;
    private final Path authorizedFacesDirectory = Paths.get("data", "authorized-faces");

    public AuthorizedFaceService(AuthorizedFaceRepository authorizedFaceRepository) {
        this.authorizedFaceRepository = authorizedFaceRepository;
    }

    public AuthorizedFaceResponse getOwnFace(UserAccount userAccount) {
        return authorizedFaceRepository.findByUserId(userAccount.getId())
                .map(this::toResponse)
                .orElseGet(this::emptyResponse);
    }

    public AuthorizedFaceResponse upsertOwnFace(UserAccount userAccount, MultipartFile image) {
        validateImage(image);

        AuthorizedFaceEntity authorizedFace = authorizedFaceRepository.findByUserId(userAccount.getId())
                .orElseGet(AuthorizedFaceEntity::new);
        authorizedFace.setUser(userAccount);
        authorizedFace.setDisplayName(slugifyName(userAccount.getFullName()));
        authorizedFace.setUpdatedAt(Instant.now().toString());
        authorizedFace.setImagePath(storeFaceImage(userAccount, image));

        return toResponse(authorizedFaceRepository.save(authorizedFace));
    }

    public void deleteOwnFace(UserAccount userAccount) {
        authorizedFaceRepository.findByUserId(userAccount.getId()).ifPresent(face -> {
            deleteFile(face.getImagePath());
            authorizedFaceRepository.delete(face);
        });
    }

    public List<DetectorAuthorizedFaceRecord> getDetectorFaces() {
        return authorizedFaceRepository.findAll().stream()
                .map(face -> new DetectorAuthorizedFaceRecord(face.getId(), face.getDisplayName(), face.getUpdatedAt()))
                .toList();
    }

    public byte[] getDetectorFaceImage(Long id) {
        AuthorizedFaceEntity face = authorizedFaceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Authorized face not found."));

        try {
            return Files.readAllBytes(Path.of(face.getImagePath()));
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not read face image.");
        }
    }

    private void validateImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please upload a face image.");
        }

        if (image.getSize() > MAX_UPLOAD_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image is too large. Keep it under 2 MB.");
        }

        String contentType = image.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only image uploads are supported.");
        }
    }

    private String storeFaceImage(UserAccount userAccount, MultipartFile image) {
        try {
            Files.createDirectories(authorizedFacesDirectory);
            String extension = extensionFrom(image.getOriginalFilename());
            Path imagePath = authorizedFacesDirectory.resolve("user-" + userAccount.getId() + extension);
            image.transferTo(imagePath);
            return imagePath.toString();
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store authorized face.");
        }
    }

    private String extensionFrom(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return ".jpg";
        }

        String extension = fileName.substring(fileName.lastIndexOf('.')).toLowerCase(Locale.ROOT);
        return List.of(".jpg", ".jpeg", ".png").contains(extension) ? extension : ".jpg";
    }

    private String slugifyName(String fullName) {
        return fullName.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
    }

    private AuthorizedFaceResponse toResponse(AuthorizedFaceEntity entity) {
        AuthorizedFaceResponse response = new AuthorizedFaceResponse();
        response.setId(entity.getId());
        response.setDisplayName(entity.getDisplayName());
        response.setUpdatedAt(entity.getUpdatedAt());
        response.setEnrolled(true);
        return response;
    }

    private AuthorizedFaceResponse emptyResponse() {
        AuthorizedFaceResponse response = new AuthorizedFaceResponse();
        response.setEnrolled(false);
        return response;
    }

    private void deleteFile(String imagePath) {
        if (imagePath == null || imagePath.isBlank()) {
            return;
        }

        try {
            Files.deleteIfExists(Path.of(imagePath));
        } catch (IOException ignored) {
        }
    }
}
