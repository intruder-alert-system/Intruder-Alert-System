package com.techsutra.intruderalert.controller;

import com.techsutra.intruderalert.entity.UserAccount;
import com.techsutra.intruderalert.model.AuthorizedFaceResponse;
import com.techsutra.intruderalert.security.AuthenticatedUser;
import com.techsutra.intruderalert.service.AuthorizedFaceService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/faces")
public class AuthorizedFaceController {
    private final AuthorizedFaceService authorizedFaceService;

    public AuthorizedFaceController(AuthorizedFaceService authorizedFaceService) {
        this.authorizedFaceService = authorizedFaceService;
    }

    @GetMapping("/me")
    public AuthorizedFaceResponse getOwnFace(Authentication authentication) {
        return authorizedFaceService.getOwnFace(currentUser(authentication));
    }

    @PostMapping("/me")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthorizedFaceResponse uploadOwnFace(
            Authentication authentication,
            @RequestParam("image") MultipartFile image
    ) {
        return authorizedFaceService.upsertOwnFace(currentUser(authentication), image);
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteOwnFace(Authentication authentication) {
        authorizedFaceService.deleteOwnFace(currentUser(authentication));
    }

    private UserAccount currentUser(Authentication authentication) {
        return ((AuthenticatedUser) authentication.getPrincipal()).getUser();
    }
}
