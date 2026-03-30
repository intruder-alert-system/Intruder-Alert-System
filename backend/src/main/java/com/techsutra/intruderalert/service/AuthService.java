package com.techsutra.intruderalert.service;

import com.techsutra.intruderalert.config.AppSecurityProperties;
import com.techsutra.intruderalert.entity.UserAccount;
import com.techsutra.intruderalert.entity.UserSession;
import com.techsutra.intruderalert.model.AuthResponse;
import com.techsutra.intruderalert.model.LoginRequest;
import com.techsutra.intruderalert.model.SignupRequest;
import com.techsutra.intruderalert.model.UserProfile;
import com.techsutra.intruderalert.repository.UserAccountRepository;
import com.techsutra.intruderalert.repository.UserSessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {
    private final UserAccountRepository userAccountRepository;
    private final UserSessionRepository userSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppSecurityProperties appSecurityProperties;

    public AuthService(
            UserAccountRepository userAccountRepository,
            UserSessionRepository userSessionRepository,
            PasswordEncoder passwordEncoder,
            AppSecurityProperties appSecurityProperties
    ) {
        this.userAccountRepository = userAccountRepository;
        this.userSessionRepository = userSessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.appSecurityProperties = appSecurityProperties;
    }

    public AuthResponse signup(SignupRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());
        if (userAccountRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered.");
        }

        UserAccount userAccount = new UserAccount();
        userAccount.setFullName(request.getFullName().trim());
        userAccount.setEmail(normalizedEmail);
        userAccount.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        userAccountRepository.save(userAccount);

        return issueSession(userAccount, request.getDeviceName());
    }

    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());
        UserAccount userAccount = userAccountRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials."));

        if (!passwordEncoder.matches(request.getPassword(), userAccount.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials.");
        }

        return issueSession(userAccount, request.getDeviceName());
    }

    public void logout(UserSession session) {
        userSessionRepository.delete(session);
    }

    public UserProfile getProfile(UserSession session) {
        return new UserProfile(
                session.getUser().getId(),
                session.getUser().getFullName(),
                session.getUser().getEmail(),
                session.getDeviceName()
        );
    }

    private AuthResponse issueSession(UserAccount userAccount, String deviceName) {
        userSessionRepository.deleteByUser(userAccount);

        UserSession session = new UserSession();
        session.setUser(userAccount);
        session.setDeviceName(deviceName.trim());
        session.setToken(UUID.randomUUID() + "-" + UUID.randomUUID());
        session.setExpiresAt(Instant.now().plus(appSecurityProperties.getTokenValidityHours(), ChronoUnit.HOURS));
        userSessionRepository.save(session);

        return new AuthResponse(
                session.getToken(),
                new UserProfile(
                        userAccount.getId(),
                        userAccount.getFullName(),
                        userAccount.getEmail(),
                        session.getDeviceName()
                )
        );
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
