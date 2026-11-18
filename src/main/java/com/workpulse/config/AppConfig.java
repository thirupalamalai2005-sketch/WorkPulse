package com.workpulse.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class AppConfig {

    /**
     * Single source of "now" and of the company time zone. Injecting a Clock (instead of calling
     * LocalDate.now() directly) lets tests use a fixed clock for deterministic attendance rules.
     */
    @Bean
    public Clock clock(@Value("${workpulse.timezone:Asia/Kolkata}") String zoneId) {
        return Clock.system(ZoneId.of(zoneId));
    }
}
