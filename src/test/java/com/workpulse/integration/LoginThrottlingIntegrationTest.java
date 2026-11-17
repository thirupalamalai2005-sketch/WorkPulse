package com.workpulse.integration;

import com.workpulse.dto.LoginRequest;
import com.workpulse.entity.Role;
import com.workpulse.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.Duration;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LoginThrottlingIntegrationTest extends AbstractIntegrationTest {

    private void attemptLogin(String email, String password, int expectedStatus) throws Exception {
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void repeatedFailedLoginsAreThrottledWithRetryAfterHeader() throws Exception {
        String email = "target@workpulse.com";
        newUser(email, Role.EMPLOYEE);

        for (int i = 0; i < 5; i++) {
            attemptLogin(email, "Wrong@Password" + i, 401);
        }

        // 6th attempt should be blocked with 429 Too Many Requests and Retry-After header
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(new LoginRequest(email, PW))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.message", containsString("Too many failed login attempts")));
    }

    @Test
    void successfulLoginResetsFailedAttemptCounter() throws Exception {
        String email = "resets@workpulse.com";
        newUser(email, Role.EMPLOYEE);

        for (int i = 0; i < 4; i++) {
            attemptLogin(email, "Wrong@Password" + i, 401);
        }

        // Successful login resets the counter
        attemptLogin(email, PW, 200);

        // 4 more failed attempts do not trigger throttle because counter was reset
        for (int i = 0; i < 4; i++) {
            attemptLogin(email, "Wrong@Password" + i, 401);
        }

        // Correct password succeeds
        attemptLogin(email, PW, 200);
    }

    @Test
    void throttlingIsScopedToAccount() throws Exception {
        String victim = "victim@workpulse.com";
        String innocent = "innocent@workpulse.com";
        newUser(victim, Role.EMPLOYEE);
        newUser(innocent, Role.EMPLOYEE);

        for (int i = 0; i < 5; i++) {
            attemptLogin(victim, "Wrong@Password" + i, 401);
        }

        // Victim is throttled
        attemptLogin(victim, PW, 429);

        // Innocent user can log in without interference
        attemptLogin(innocent, PW, 200);
    }

    @Test
    void lockoutExpiresWhenClockAdvances() throws Exception {
        String email = "expiring@workpulse.com";
        newUser(email, Role.EMPLOYEE);

        for (int i = 0; i < 5; i++) {
            attemptLogin(email, "Wrong@Password" + i, 401);
        }
        attemptLogin(email, PW, 429);

        // Advance the test clock by 301 seconds (past 300-second lockout)
        clock.setLocal("2026-03-04T10:06"); // 6 minutes later

        // Now user can log in successfully
        attemptLogin(email, PW, 200);
    }

    @Test
    void spoofedForwardedForHeaderDoesNotBypassThrottling() throws Exception {
        String email = "spoof@workpulse.com";
        newUser(email, Role.EMPLOYEE);

        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login")
                    .header("X-Forwarded-For", "198.51.100." + i)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsString(new LoginRequest(email, "Wrong@Password" + i))))
                    .andExpect(status().isUnauthorized());
        }

        // Attacker trying to bypass throttle with different spoofed IP is still blocked
        mvc.perform(post("/api/auth/login")
                .header("X-Forwarded-For", "203.0.113.99")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(new LoginRequest(email, PW))))
                .andExpect(status().isTooManyRequests());
    }
}
