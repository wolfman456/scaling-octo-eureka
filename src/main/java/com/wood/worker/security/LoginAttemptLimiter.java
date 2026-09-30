package com.wood.worker.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Counts failed admin logins per key and refuses further attempts once a key has
 * failed too often inside the window.
 *
 * <p>Basic auth on {@code /api/admin/**} is otherwise an unthrottled oracle: an
 * attacker can try passwords as fast as bcrypt cost 10 allows, forever, with no
 * signal that anything is wrong.
 *
 * <p>Two keys are tracked per failure — the source IP and the attempted username —
 * and both must be clear to let a request through. Brute force from many IPs is
 * stopped by the username bucket; password spraying across many usernames is
 * stopped by the IP bucket.
 *
 * <p>State is in memory and per JVM instance, so a restart or a second replica
 * clears it. That is a deliberate trade for this deployment: a persistent store
 * would need its own credentials and its own failure mode, and an admin login
 * limiter is not worth either. A lockout is time-boxed rather than permanent
 * precisely because it lives only in one process.
 */
@Component
public class LoginAttemptLimiter {

    private final int maxAttempts;
    private final Duration window;
    private final Duration lockout;
    private final Clock clock;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public LoginAttemptLimiter(@Value("${app.admin.max-login-attempts:5}") int maxAttempts,
                               @Value("${app.admin.login-attempt-window:PT15M}") Duration window,
                               @Value("${app.admin.login-lockout:PT15M}") Duration lockout,
                               Clock clock) {
        this.maxAttempts = maxAttempts;
        this.window = window;
        this.lockout = lockout;
        this.clock = clock;
    }

    /** True when this key has failed {@code maxAttempts} times and is still locked. */
    public boolean isLocked(String key, String username) {
        Instant now = clock.instant();
        return isLocked(key, now) || (username != null && isLocked(keyFor(username), now));
    }

    private boolean isLocked(String key, Instant now) {
        Bucket bucket = buckets.get(key);
        if (bucket == null) {
            return false;
        }
        if (bucket.lockedUntil != null && bucket.lockedUntil.isAfter(now)) {
            return true;
        }
        if (bucket.lockedUntil != null) {
            // Lockout expired: the key starts clean, so a later legitimate login
            // is not remembered as suspicious forever.
            buckets.remove(key, bucket);
        }
        return false;
    }

    public void recordFailure(String key, String username) {
        Instant now = clock.instant();
        lock(buckets.computeIfAbsent(key, k -> new Bucket()), now);
        if (username != null) {
            lock(buckets.computeIfAbsent(keyFor(username), k -> new Bucket()), now);
        }
    }

    private void lock(Bucket bucket, Instant now) {
        synchronized (bucket) {
            if (bucket.firstFailure == null || !now.isBefore(bucket.firstFailure.plus(window))) {
                // The previous failures have aged out of the window, so the count
                // starts over. Without this the counter is cumulative and a user
                // who mistyped once a month would eventually lock themselves out.
                bucket.firstFailure = now;
                bucket.failures.set(0);
            }
            if (bucket.failures.incrementAndGet() >= maxAttempts) {
                bucket.lockedUntil = now.plus(lockout);
            }
        }
    }

    /** A successful login clears the key, so a typo does not accumulate toward a lockout. */
    public void recordSuccess(String key, String username) {
        buckets.remove(key);
        if (username != null) {
            buckets.remove(keyFor(username));
        }
    }

    public long lockoutSeconds() {
        return lockout.toSeconds();
    }

    static String keyFor(String value) {
        return value == null ? null : value.toLowerCase();
    }

    int size() {
        return buckets.size();
    }

    private static final class Bucket {
        private final AtomicInteger failures = new AtomicInteger();
        private Instant firstFailure;
        private Instant lockedUntil;
    }
}
