package com.workpulse.security;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers "all sessions of this user created before time T are invalid".
 * Used when an account is deactivated, its role changes or its password changes, so that
 * already-open sessions stop working immediately (not only at the next login).
 * In-memory is sufficient because the HTTP sessions themselves are in-memory too.
 */
@Component
public class SessionRevocationService {

    private final ConcurrentHashMap<String, Long> revokedAt = new ConcurrentHashMap<>();

    public void revokeAllSessions(String email) {
        if (email != null) revokedAt.put(email.toLowerCase().trim(), System.currentTimeMillis());
    }

    public boolean isRevoked(String email, long sessionCreationMillis) {
        Long t = revokedAt.get(email.toLowerCase().trim());
        return t != null && sessionCreationMillis <= t;
    }
}
