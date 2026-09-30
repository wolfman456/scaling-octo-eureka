package com.wood.worker.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Applies {@link LoginAttemptLimiter} to admin Basic auth.
 *
 * <p>Runs ahead of authentication so a locked key is refused with 429 rather than
 * costing a bcrypt comparison — otherwise the limiter would only make brute force
 * slower, not stop it, which is the actual complaint.
 *
 * <p>Outcomes are read back off the response rather than wired into Spring Security's
 * success/failure handlers. The 401 itself is left entirely to the framework: it owns
 * the {@code WWW-Authenticate} challenge that makes the browser prompt for credentials,
 * and replacing that body here would break the prompt.
 */
public class AdminLoginThrottleFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AdminLoginThrottleFilter.class);
    private static final String ADMIN_PREFIX = "/api/admin/";

    private final LoginAttemptLimiter limiter;

    public AdminLoginThrottleFilter(LoginAttemptLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!request.getRequestURI().startsWith(ADMIN_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String key = clientKey(request);
        String username = attemptedUsername(request);
        if (limiter.isLocked(key, username)) {
            log.warn("Refusing admin request from {}: too many failed login attempts", key);
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(limiter.lockoutSeconds()));
            response.getWriter().write("{\"status\":429,\"message\":\"Too many failed login attempts. "
                    + "Try again later.\",\"fieldErrors\":null}");
            return;
        }

        chain.doFilter(request, response);

        // 401 is what a wrong or missing credential produces. Anything else that
        // carried credentials was accepted, so the key starts clean again.
        if (response.getStatus() == HttpServletResponse.SC_UNAUTHORIZED) {
            limiter.recordFailure(key, username);
        } else if (username != null) {
            limiter.recordSuccess(key, username);
        }
    }

    /**
     * The client address, preferring {@code X-Forwarded-For} because the app sits behind
     * Railway's proxy and every request would otherwise share one bucket.
     */
    static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }

    /**
     * The username being attempted, read from the Basic header before authentication
     * runs. A malformed or absent header yields null, which limits that key by IP only.
     */
    static String attemptedUsername(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, "Basic ", 0, 6)) {
            return null;
        }
        try {
            String decoded = new String(Base64.getDecoder().decode(header.substring(6).trim()),
                    StandardCharsets.UTF_8);
            int colon = decoded.indexOf(':');
            String user = colon < 0 ? decoded : decoded.substring(0, colon);
            return user.isBlank() ? null : user;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
