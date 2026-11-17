package com.workpulse.unit;

import com.workpulse.security.LoginRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LoginRateLimiterTest {

    private static class SettableClock extends Clock {
        private Instant now;
        private final ZoneId zone;

        SettableClock(Instant now, ZoneId zone) {
            this.now = now;
            this.zone = zone;
        }

        void advance(Duration d) {
            this.now = this.now.plus(d);
        }

        @Override public ZoneId getZone() { return zone; }
        @Override public Clock withZone(ZoneId zone) { return new SettableClock(now, zone); }
        @Override public Instant instant() { return now; }
    }

    private SettableClock clock;
    private LoginRateLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new SettableClock(Instant.parse("2026-03-04T10:00:00Z"), ZoneId.of("UTC"));
        limiter = new LoginRateLimiter(5, 300, clock);
    }

    @Test
    void startsUnblocked() {
        assertFalse(limiter.isBlocked("alice@wp.com"));
        assertEquals(0, limiter.getRemainingLockoutSeconds("alice@wp.com"));
        assertFalse(limiter.isBlocked(null));
    }

    @Test
    void fourFailedAttemptsDoesNotBlock() {
        for (int i = 0; i < 4; i++) {
            limiter.recordFailedAttempt("alice@wp.com");
        }
        assertFalse(limiter.isBlocked("alice@wp.com"));
        assertEquals(0, limiter.getRemainingLockoutSeconds("alice@wp.com"));
    }

    @Test
    void fifthFailedAttemptTriggersLockout() {
        for (int i = 0; i < 5; i++) {
            limiter.recordFailedAttempt("alice@wp.com");
        }
        assertTrue(limiter.isBlocked("alice@wp.com"));
        assertTrue(limiter.getRemainingLockoutSeconds("alice@wp.com") > 0);
        assertTrue(limiter.getRemainingLockoutSeconds("alice@wp.com") <= 300);
    }

    @Test
    void keyIsCaseAndWhitespaceInsensitive() {
        for (int i = 0; i < 5; i++) {
            limiter.recordFailedAttempt("  Alice@WP.COM  ");
        }
        assertTrue(limiter.isBlocked("alice@wp.com"));
        assertTrue(limiter.isBlocked("ALICE@WP.COM"));
    }

    @Test
    void lockoutExpiresAfterDuration() {
        for (int i = 0; i < 5; i++) {
            limiter.recordFailedAttempt("alice@wp.com");
        }
        assertTrue(limiter.isBlocked("alice@wp.com"));

        // Advance 299 seconds - still blocked
        clock.advance(Duration.ofSeconds(299));
        assertTrue(limiter.isBlocked("alice@wp.com"));

        // Advance 2 more seconds (total 301 seconds) - unblocked
        clock.advance(Duration.ofSeconds(2));
        assertFalse(limiter.isBlocked("alice@wp.com"));
        assertEquals(0, limiter.getRemainingLockoutSeconds("alice@wp.com"));
    }

    @Test
    void resetAttemptsUnblocksImmediately() {
        for (int i = 0; i < 5; i++) {
            limiter.recordFailedAttempt("alice@wp.com");
        }
        assertTrue(limiter.isBlocked("alice@wp.com"));

        limiter.resetAttempts("alice@wp.com");
        assertFalse(limiter.isBlocked("alice@wp.com"));
    }

    @Test
    void resetAllClearsAllThrottledKeys() {
        for (int i = 0; i < 5; i++) {
            limiter.recordFailedAttempt("alice@wp.com");
            limiter.recordFailedAttempt("bob@wp.com");
        }
        assertTrue(limiter.isBlocked("alice@wp.com"));
        assertTrue(limiter.isBlocked("bob@wp.com"));

        limiter.resetAll();
        assertFalse(limiter.isBlocked("alice@wp.com"));
        assertFalse(limiter.isBlocked("bob@wp.com"));
    }

    @Test
    void independentKeysDoNotInterfere() {
        for (int i = 0; i < 5; i++) {
            limiter.recordFailedAttempt("alice@wp.com");
        }
        assertTrue(limiter.isBlocked("alice@wp.com"));
        assertFalse(limiter.isBlocked("bob@wp.com"));
    }

    @Test
    void threadSafetyUnderConcurrentAttempts() throws Exception {
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    limiter.recordFailedAttempt("concurrent@wp.com");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        // 10 attempts >= max 5 -> must be blocked, counter exact with zero lost updates
        assertTrue(limiter.isBlocked("concurrent@wp.com"));
        assertEquals(10, limiter.getFailedAttempts("concurrent@wp.com"), "Counter must record all 10 concurrent failures with no lost updates");
    }
}
