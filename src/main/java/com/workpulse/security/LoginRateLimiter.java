package com.workpulse.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory thread-safe rate limiter and throttling manager for failed login attempts.
 * Configured via application.properties:
 * - maxAttempts: maximum failed logins before throttling
 * - lockoutDurationSeconds: duration of temporary throttling window
 *
 * Does NOT permanently lock user accounts in the database.
 */
@Component
public class LoginRateLimiter {

    private static final Logger logger = LoggerFactory.getLogger(LoginRateLimiter.class);

    private final int maxAttempts;
    private final long lockoutDurationSeconds;
    private final Clock clock;

    private static class AttemptMetadata {
        int failedAttempts;
        Instant lockExpiration;
        Instant lastFailure;

        AttemptMetadata(int failedAttempts, Instant lockExpiration, Instant now) {
            this.failedAttempts = failedAttempts;
            this.lockExpiration = lockExpiration;
            this.lastFailure = now;
        }
    }

    private final ConcurrentHashMap<String, AttemptMetadata> attemptsMap = new ConcurrentHashMap<>();

    @Autowired
    public LoginRateLimiter(
            @Value("${security.login.rate-limit.max-attempts:5}") int maxAttempts,
            @Value("${security.login.rate-limit.lockout-duration-seconds:300}") long lockoutDurationSeconds,
            Clock clock) {
        this.maxAttempts = maxAttempts;
        this.lockoutDurationSeconds = lockoutDurationSeconds;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    public LoginRateLimiter(int maxAttempts, long lockoutDurationSeconds) {
        this(maxAttempts, lockoutDurationSeconds, Clock.systemUTC());
    }

    /**
     * Checks whether the given key (normalized email or IP) is currently blocked.
     *
     * @param key identifier for the login attempt (e.g., lowercase email)
     * @return true if currently rate-limited/throttled; false otherwise
     */
    public boolean isBlocked(String key) {
        if (key == null) return false;
        String normalizedKey = key.toLowerCase().trim();
        AttemptMetadata metadata = attemptsMap.get(normalizedKey);

        if (metadata == null) {
            return false;
        }

        Instant now = Instant.now(clock);
        if (metadata.lockExpiration != null) {
            if (now.isBefore(metadata.lockExpiration)) {
                return true;
            } else {
                // Lockout period expired; reset attempt counter
                attemptsMap.remove(normalizedKey);
                return false;
            }
        }

        return false;
    }

    /**
     * Records a failed login attempt. If failed attempts exceed maxAttempts,
     * triggers temporary throttling for lockoutDurationSeconds.
     *
     * @param key identifier for the login attempt
     */
    public void recordFailedAttempt(String key) {
        if (key == null) return;
        String normalizedKey = key.toLowerCase().trim();
        Instant now = Instant.now(clock);

        // Keep memory bounded: drop stale entries when the map grows large.
        if (attemptsMap.size() > 10_000) {
            Instant cutoff = now.minusSeconds(lockoutDurationSeconds);
            attemptsMap.entrySet().removeIf(e -> e.getValue().lastFailure.isBefore(cutoff));
        }

        attemptsMap.compute(normalizedKey, (k, meta) -> {
            if (meta == null) {
                return new AttemptMetadata(1, null, now);
            }

            // If already expired, restart count
            if (meta.lockExpiration != null && now.isAfter(meta.lockExpiration)) {
                return new AttemptMetadata(1, null, now);
            }

            meta.failedAttempts += 1;
            meta.lastFailure = now;
            if (meta.failedAttempts >= maxAttempts) {
                meta.lockExpiration = now.plusSeconds(lockoutDurationSeconds);
                logger.warn("Too many failed login attempts for key. Temporary throttle activated for {} seconds.", lockoutDurationSeconds);
            }
            return meta;
        });
    }

    /**
     * Resets failed login attempts after a successful login.
     *
     * @param key identifier for the login attempt
     */
    public void resetAttempts(String key) {
        if (key == null) return;
        attemptsMap.remove(key.toLowerCase().trim());
    }

    /**
     * Resets all throttling state. Used by tests to avoid cross-test pollution.
     */
    public void resetAll() {
        attemptsMap.clear();
    }

    /**
     * Returns remaining lockout seconds if blocked, or 0 if not blocked.
     */
    public long getRemainingLockoutSeconds(String key) {
        if (key == null) return 0;
        AttemptMetadata metadata = attemptsMap.get(key.toLowerCase().trim());
        if (metadata != null && metadata.lockExpiration != null) {
            long remaining = metadata.lockExpiration.getEpochSecond() - Instant.now(clock).getEpochSecond();
            return Math.max(0, remaining);
        }
        return 0;
    }

    /**
     * Returns the current count of failed attempts for the key.
     */
    public int getFailedAttempts(String key) {
        if (key == null) return 0;
        AttemptMetadata metadata = attemptsMap.get(key.toLowerCase().trim());
        return metadata != null ? metadata.failedAttempts : 0;
    }
}
