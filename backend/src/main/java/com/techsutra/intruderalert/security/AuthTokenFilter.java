package com.techsutra.intruderalert.security;

import com.techsutra.intruderalert.entity.UserSession;
import com.techsutra.intruderalert.repository.UserSessionRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

@Component
public class AuthTokenFilter extends OncePerRequestFilter {
    private final UserSessionRepository userSessionRepository;

    public AuthTokenFilter(UserSessionRepository userSessionRepository) {
        this.userSessionRepository = userSessionRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String token = extractToken(request);
        if (token != null && !token.isBlank()) {
            userSessionRepository.findByToken(token)
                    .filter(session -> session.getExpiresAt().isAfter(Instant.now()))
                    .ifPresent(session -> setAuthentication(session, request));
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }

        return request.getParameter("access_token");
    }

    private void setAuthentication(UserSession session, HttpServletRequest request) {
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(session.getUser(), session);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        authenticatedUser,
                        null,
                        AuthorityUtils.NO_AUTHORITIES
                );
        authentication.setDetails(request);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
