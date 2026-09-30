package com.wood.worker.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginAttemptLimiterTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private final MutableClock clock = new MutableClock(T0);
    private final LoginAttemptLimiter limiter =
            new LoginAttemptLimiter(3, Duration.ofMinutes(15), Duration.ofMinutes(15), clock);

    @Test
    void allowsAttemptsUpToTheLimit() {
        for (int i = 0; i < 3; i++) {
            assertFalse(limiter.isLocked("1.2.3.4", "admin"), "attempt " + i + " should be allowed");
            limiter.recordFailure("1.2.3.4", "admin");
        }
    }

    @Test
    void locksTheIpAndTheUsernameAfterTooManyFailures() {
        for (int i = 0; i < 3; i++) {
            limiter.recordFailure("1.2.3.4", "admin");
        }

        assertTrue(limiter.isLocked("1.2.3.4", "admin"));
        // The username bucket is what stops a distributed attempt on one account.
        assertTrue(limiter.isLocked("9.9.9.9", "admin"));
        // The ip bucket is deliberately username-agnostic: that is what stops password
        // spraying (one password, many accounts) from the same host.
        assertTrue(limiter.isLocked("1.2.3.4", "editor"));
        // A different host is unaffected.
        assertFalse(limiter.isLocked("9.9.9.9", "editor"));
    }

    @Test
    void lockoutExpires() {
        for (int i = 0; i < 3; i++) {
            limiter.recordFailure("1.2.3.4", "admin");
        }
        assertTrue(limiter.isLocked("1.2.3.4", "admin"));

        clock.advance(Duration.ofMinutes(16));

        assertFalse(limiter.isLocked("1.2.3.4", "admin"));
    }

    @Test
    void aSuccessClearsTheCountSoTyposDoNotAccumulate() {
        limiter.recordFailure("1.2.3.4", "admin");
        limiter.recordFailure("1.2.3.4", "admin");
        limiter.recordSuccess("1.2.3.4", "admin");
        limiter.recordFailure("1.2.3.4", "admin");
        limiter.recordFailure("1.2.3.4", "admin");

        assertFalse(limiter.isLocked("1.2.3.4", "admin"));
    }

    @Test
    void theWindowResetsTheCountAfterEnoughTime() {
        limiter.recordFailure("1.2.3.4", "admin");
        clock.advance(Duration.ofMinutes(16));
        limiter.recordFailure("1.2.3.4", "admin");
        limiter.recordFailure("1.2.3.4", "admin");

        assertFalse(limiter.isLocked("1.2.3.4", "admin"));
    }

    @Test
    void usernameMatchingIsCaseInsensitive() {
        for (int i = 0; i < 3; i++) {
            limiter.recordFailure("1.2.3.4", "Admin");
        }

        assertTrue(limiter.isLocked("9.9.9.9", "admin"));
    }

    @Test
    void aNullUsernameStillLimitsTheIp() {
        for (int i = 0; i < 3; i++) {
            limiter.recordFailure("1.2.3.4", null);
        }

        assertTrue(limiter.isLocked("1.2.3.4", null));
        assertTrue(limiter.isLocked("1.2.3.4", "admin"), "the ip bucket is the only one in play");
    }

    @Test
    void expiredEntriesAreNotKeptForever() {
        for (int i = 0; i < 3; i++) {
            limiter.recordFailure("1.2.3.4", "admin");
        }
        assertTrue(limiter.size() > 0);

        clock.advance(Duration.ofMinutes(16));
        limiter.isLocked("1.2.3.4", "admin");

        assertEquals(0, limiter.size(), "stale buckets must be dropped, not just ignored");
    }

    @Test
    void readsTheClientAddressFromForwardedHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.9, 70.41.3.18, 150.172.238.178");
        assertEquals("203.0.113.9", AdminLoginThrottleFilter.clientKey(request));

        MockHttpServletRequest realIp = new MockHttpServletRequest();
        realIp.addHeader("X-Real-IP", "198.51.100.7");
        assertEquals("198.51.100.7", AdminLoginThrottleFilter.clientKey(realIp));

        MockHttpServletRequest direct = new MockHttpServletRequest();
        direct.setRemoteAddr("192.0.2.5");
        assertEquals("192.0.2.5", AdminLoginThrottleFilter.clientKey(direct));
    }

    @Test
    void readsTheAttemptedUsernameWithoutAuthenticating() {
        assertEquals("admin", AdminLoginThrottleFilter.attemptedUsername(basic("YWRtaW46c2VjcmV0")));
        assertEquals("admin", AdminLoginThrottleFilter.attemptedUsername(basic("YWRtaW46")));
    }

    @Test
    void toleratesMissingOrMalformedCredentials() {
        assertEquals(null, AdminLoginThrottleFilter.attemptedUsername(new MockHttpServletRequest()));
        assertEquals(null, AdminLoginThrottleFilter.attemptedUsername(basic("not-base64!!")));
        assertEquals(null, AdminLoginThrottleFilter.attemptedUsername(basic("Og==")), "empty username");
    }

    private static MockHttpServletRequest basic(String base64) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic " + base64);
        return request;
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration amount) {
            now = now.plus(amount);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
