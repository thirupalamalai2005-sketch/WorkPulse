package com.workpulse.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.ZoneId;

@TestConfiguration
public class TestClockConfig {
    /** Wednesday 4 March 2026, 10:00 in the company zone. */
    public static final String DEFAULT_NOW = "2026-03-04T10:00";

    @Bean
    @Primary
    public MutableClock testClock() {
        MutableClock clock = new MutableClock(java.time.Instant.EPOCH, ZoneId.of("Asia/Kolkata"));
        clock.setLocal(DEFAULT_NOW);
        return clock;
    }
}
