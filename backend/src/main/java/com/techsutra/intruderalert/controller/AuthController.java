package com.techsutra.intruderalert.controller;

import com.techsutra.intruderalert.entity.UserSession;
import com.techsutra.intruderalert.model.AuthResponse;
import com.techsutra.intruderalert.model.LoginRequest;
import com.techsutra.intruderalert.model.SignupRequest;
import com.techsutra.intruderalert.model.UserProfile;
import com.techsutra.intruderalert.security.AuthenticatedUser;
import com.techsutra.intruderalert.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse signup(@Valid @RequestBody SignupRequest request) {
        return authService.signup(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public UserProfile me(Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        return authService.getProfile(authenticatedUser.getSession());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        UserSession session = authenticatedUser.getSession();
        authService.logout(session);
    }
}
