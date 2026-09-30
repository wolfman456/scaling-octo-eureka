package com.wood.worker.controller;

import com.wood.worker.TestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The limiter is only useful if it survives the real request path, so this drives
 * actual HTTP with wrong credentials and checks that brute force stops instead of
 * continuing indefinitely.
 *
 * <p>Not {@code @Transactional}: the limiter keeps its counters in memory across
 * requests, and a rolled-back test transaction would not reflect that.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ratelimit;DB_CLOSE_DELAY=-1;MODE=LEGACY",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.upload-dir=target/test-uploads",
        "app.admin.user=admin",
        "app.admin.password=test-password",
        "app.admin.max-login-attempts=3"
})
@AutoConfigureMockMvc
class AdminLoginThrottleTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.wood.worker.security.LoginAttemptLimiter limiter;

    private static final String WRONG = basic("admin", "wrong-password");

    @BeforeEach
    void resetLimiter() {
        // The limiter is a singleton, so a leftover lock from another test would
        // make the first request here fail for the wrong reason.
        limiter.recordSuccess("127.0.0.1", "admin");
    }

    @Test
    void repeatedWrongPasswordsAreEventuallyRefused() throws Exception {
        for (int attempt = 1; attempt <= 3; attempt++) {
            mockMvc.perform(get("/api/admin/media").header(HttpHeaders.AUTHORIZATION, WRONG))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(get("/api/admin/media").header(HttpHeaders.AUTHORIZATION, WRONG))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(headerExists("Retry-After"));
    }

    @Test
    void aLockedKeyIsRefusedEvenWithTheCorrectPassword() throws Exception {
        for (int attempt = 1; attempt <= 3; attempt++) {
            mockMvc.perform(get("/api/admin/media").header(HttpHeaders.AUTHORIZATION, WRONG))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(get("/api/admin/media")
                        .header(HttpHeaders.AUTHORIZATION, basic("admin", "test-password")))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void aCorrectPasswordFromAnotherHostIsStillRefused() throws Exception {
        // The username bucket is global on purpose: a distributed attack on the one
        // admin account is the threat that matters, and it is spread across source
        // addresses by definition. The cost is that an attacker can also lock the
        // real admin out for the duration -- see the trade-off note in DESIGN.md.
        for (int attempt = 1; attempt <= 3; attempt++) {
            mockMvc.perform(get("/api/admin/media")
                            .header(HttpHeaders.AUTHORIZATION, WRONG)
                            .header("X-Forwarded-For", "10.0.0.9"))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(get("/api/admin/media")
                        .header(HttpHeaders.AUTHORIZATION, basic("admin", "test-password"))
                        .header("X-Forwarded-For", "10.0.0.8"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void sprayingOtherUsernamesFromOneHostStops() throws Exception {
        // The ip bucket is username-agnostic, which is what stops one password being
        // tried against many accounts from a single host.
        for (int attempt = 1; attempt <= 3; attempt++) {
            mockMvc.perform(get("/api/admin/media")
                            .header(HttpHeaders.AUTHORIZATION, basic("victim" + attempt, "guess"))
                            .header("X-Forwarded-For", "10.0.0.6"))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(get("/api/admin/media")
                        .header(HttpHeaders.AUTHORIZATION, basic("fresh", "guess"))
                        .header("X-Forwarded-For", "10.0.0.6"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void aSuccessfulLoginClearsTheCount() throws Exception {
        mockMvc.perform(get("/api/admin/media")
                        .header(HttpHeaders.AUTHORIZATION, WRONG)
                        .header("X-Forwarded-For", "10.0.0.2"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/media")
                        .header(HttpHeaders.AUTHORIZATION, basic("admin", "test-password"))
                        .header("X-Forwarded-For", "10.0.0.2"))
                .andExpect(status().isOk());

        // Two more failures must not be enough to lock out, because the success
        // reset the count.
        mockMvc.perform(get("/api/admin/media")
                        .header(HttpHeaders.AUTHORIZATION, WRONG)
                        .header("X-Forwarded-For", "10.0.0.2"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/media")
                        .header(HttpHeaders.AUTHORIZATION, WRONG)
                        .header("X-Forwarded-For", "10.0.0.2"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicEndpointsAreNeverThrottled() throws Exception {
        for (int attempt = 1; attempt <= 5; attempt++) {
            mockMvc.perform(get("/api/gallery")).andExpect(status().isOk());
        }
    }

    @Test
    void thePublicApiIsNotAffectedByAnAdminLockout() throws Exception {
        for (int attempt = 1; attempt <= 3; attempt++) {
            mockMvc.perform(get("/api/admin/media")
                            .header(HttpHeaders.AUTHORIZATION, WRONG)
                            .header("X-Forwarded-For", "10.0.0.7"))
                    .andExpect(status().isUnauthorized());
        }
        assertTrue(limiter.isLocked("10.0.0.7", "admin"));

        mockMvc.perform(get("/api/gallery").header("X-Forwarded-For", "10.0.0.7"))
                .andExpect(status().isOk());
    }

    private static org.springframework.test.web.servlet.ResultMatcher headerExists(String name) {
        return result -> assertTrue(result.getResponse().getHeader(name) != null,
                "expected header " + name);
    }

    private static String basic(String user, String password) {
        return "Basic " + Base64.getEncoder().encodeToString(
                (user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }
}
