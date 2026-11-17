package com.workpulse.integration;

import com.workpulse.dto.AdminCreateUserRequest;
import com.workpulse.dto.LoginRequest;
import com.workpulse.dto.RegisterRequest;
import com.workpulse.entity.Employee;
import com.workpulse.entity.Role;
import com.workpulse.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthIntegrationTest extends AbstractIntegrationTest {

    private MockHttpSession login(String email, String password) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(new LoginRequest(email, password)))).andExpect(status().isOk()).andReturn();
        return (MockHttpSession) r.getRequest().getSession();
    }

    private void register(String email, String password, int expectedStatus) throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(new RegisterRequest("New Person", email, password))))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void registerCreatesEmployeeWithEmployeeProfile() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(new RegisterRequest("New Person", "new.person@workpulse.com", PW))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role", is("EMPLOYEE")))
                .andExpect(jsonPath("$.employeeCode", startsWith("WP-")))
                .andExpect(jsonPath("$.password").doesNotExist());
        assertTrue(employeeRepository.findByUser_Email("new.person@workpulse.com").isPresent());
    }

    @Test
    void publicRegistrationCannotGrantAdminRole() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("username", "Sneaky", "email", "sneaky@workpulse.com",
                        "password", PW, "role", "ADMIN"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role", is("EMPLOYEE")));
    }

    @Test
    void duplicateEmailIsRejectedCaseInsensitively() throws Exception {
        register("dup@workpulse.com", PW, 201);
        register("DUP@workpulse.com", PW, 409);
    }

    @Test
    void passwordPolicyIsEnforcedOnRegistration() throws Exception {
        register("short@workpulse.com", "Ab1!", 400);
        register("nodigits@workpulse.com", "NoDigitsHere!!", 400);
        register("long@workpulse.com", "Aa1!" + "x".repeat(130), 400);
    }

    @Test
    void loginSucceedsAndWrongCredentialsGiveOneGenericMessage() throws Exception {
        newUser("login.ok@workpulse.com", Role.EMPLOYEE);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(new LoginRequest("login.ok@workpulse.com", PW))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role", is("EMPLOYEE")));
        for (String[] bad : new String[][]{{"login.ok@workpulse.com", "Wrong@Password999"}, {"nobody@workpulse.com", PW}}) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsString(new LoginRequest(bad[0], bad[1]))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message", is("Invalid email or password")));
        }
    }

    @Test
    void deactivatedAccountCannotLogIn() throws Exception {
        newUser("disabled@workpulse.com", Role.EMPLOYEE);
        jdbc.update("UPDATE users SET enabled = FALSE WHERE email = ?", "disabled@workpulse.com");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(new LoginRequest("disabled@workpulse.com", PW))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("Invalid email or password")));
    }

    @Test
    void protectedApiRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/dashboard/summary")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    void logoutInvalidatesTheSession() throws Exception {
        newUser("logout@workpulse.com", Role.EMPLOYEE);
        MockHttpSession session = login("logout@workpulse.com", PW);
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isOk());
        assertTrue(session.isInvalid(), "session must be invalidated by logout");
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void loginRotatesAnExistingSession() throws Exception {
        newUser("fixation@workpulse.com", Role.EMPLOYEE);
        MockHttpSession pre = new MockHttpSession();
        MvcResult r = mvc.perform(post("/api/auth/login").session(pre).contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(new LoginRequest("fixation@workpulse.com", PW)))).andExpect(status().isOk()).andReturn();
        assertTrue(pre.isInvalid(), "pre-login session must be discarded (session fixation protection)");
        assertNotSame(pre, r.getRequest().getSession());
    }

    @Test
    void repeatedFailedLoginsAreThrottled() throws Exception {
        String email = "throttle@workpulse.com";
        limiter.resetAttempts(email);
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsString(new LoginRequest(email, "Wrong@Password" + i)))).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(new LoginRequest(email, "Any@Password123"))))
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
        limiter.resetAttempts(email);
    }

    @Test
    void stateChangingRequestsNeedACsrfToken() throws Exception {
        newUser("admin.csrf@workpulse.com", Role.ADMIN);
        MockHttpSession session = login("admin.csrf@workpulse.com", PW);
        AdminCreateUserRequest body = new AdminCreateUserRequest("Created", "created@workpulse.com", PW, Role.EMPLOYEE);
        mvc.perform(post("/api/admin/users").session(session).contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(body))).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/users").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(body))).andExpect(status().isCreated());
    }

    @Test
    void deactivatingAnAccountEndsItsLiveSessions() throws Exception {
        Employee admin = newUser("admin.deact@workpulse.com", Role.ADMIN);
        Employee victim = newUser("victim.deact@workpulse.com", Role.EMPLOYEE);
        MockHttpSession session = login("victim.deact@workpulse.com", PW);
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());

        doPatch("/api/employees/" + victim.getId() + "/status", Map.of("active", false), as(admin)).andExpect(status().isOk());

        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void changingPasswordRequiresCurrentPasswordAndEndsSessions() throws Exception {
        newUser("pwchange@workpulse.com", Role.EMPLOYEE);
        MockHttpSession session = login("pwchange@workpulse.com", PW);
        String newPw = "Another@Password456";

        mvc.perform(post("/api/account/password").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("currentPassword", "Wrong@Password000", "newPassword", newPw))))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/account/password").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("currentPassword", PW, "newPassword", newPw)))).andExpect(status().isOk());

        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(new LoginRequest("pwchange@workpulse.com", PW)))).andExpect(status().isUnauthorized());
        login("pwchange@workpulse.com", newPw);
    }

    @Test
    void securityHeadersArePresent() throws Exception {
        mvc.perform(get("/api/auth/config")).andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationEnabled", is(true)))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")));
    }

    @Test
    void expiredSessionOnStateChangingRequestReturnsUnauthorized() throws Exception {
        newUser("expired@workpulse.com", Role.EMPLOYEE);
        MockHttpSession session = login("expired@workpulse.com", PW);
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());

        session.invalidate();

        mvc.perform(post("/api/attendance/check-in").session(session).with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }
}
