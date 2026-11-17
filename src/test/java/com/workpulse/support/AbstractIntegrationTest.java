package com.workpulse.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workpulse.entity.Employee;
import com.workpulse.entity.Role;
import com.workpulse.repository.EmployeeRepository;
import com.workpulse.security.LoginRateLimiter;
import com.workpulse.service.UserProvisioningService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Base for all integration tests: full application context, in-memory H2 (MySQL mode, schema created by the
 * real Flyway migrations), a movable clock, and a clean set of tables before every test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestClockConfig.class)
public abstract class AbstractIntegrationTest {

    public static final String PW = "Strong@Password123";

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper om;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected UserProvisioningService provisioning;
    @Autowired protected EmployeeRepository employeeRepository;
    @Autowired protected LoginRateLimiter limiter;
    @Autowired protected MutableClock clock;

    @BeforeEach
    void cleanDatabaseAndClock() {
        // children first; employees reference each other through manager_id
        for (String t : new String[]{"notifications", "leave_requests", "attendance_corrections", "attendance_records", "audit_logs", "holidays"}) {
            jdbc.update("DELETE FROM " + t);
        }
        jdbc.update("UPDATE employees SET manager_id = NULL");
        jdbc.update("DELETE FROM employees");
        jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM departments");
        // restore seeded reference data that tests may modify
        jdbc.update("UPDATE leave_types SET annual_entitlement = 18, allow_past_dates = FALSE, active = TRUE WHERE code = 'ANNUAL'");
        jdbc.update("UPDATE leave_types SET annual_entitlement = 10, allow_past_dates = TRUE, active = TRUE WHERE code = 'SICK'");
        jdbc.update("UPDATE leave_types SET annual_entitlement = 6, allow_past_dates = FALSE, active = TRUE WHERE code = 'CASUAL'");
        jdbc.update("UPDATE work_schedules SET start_time = '09:00:00', end_time = '18:00:00', grace_minutes = 15, working_days = 'MON,TUE,WED,THU,FRI' WHERE id = 1");
        limiter.resetAll();
        clock.setLocal(TestClockConfig.DEFAULT_NOW);
    }

    // ------------------------------------------------------------ data helpers

    protected Employee newUser(String email, Role role) {
        return newUser(email, role, null);
    }

    protected Employee newUser(String email, Role role, Employee manager) {
        String name = email.substring(0, email.indexOf('@'));
        limiter.resetAttempts(email);
        return provisioning.provision(name, email, PW, role, null, manager, "Tester", "+91 98765 43210", LocalDate.of(2025, 1, 1));
    }

    protected Long leaveTypeId(String code) {
        return jdbc.queryForObject("SELECT id FROM leave_types WHERE code = ?", Long.class, code);
    }

    protected RequestPostProcessor as(Employee e) {
        return user(e.getUser().getEmail()).roles(e.getUser().getRole().name());
    }

    // ------------------------------------------------------------ request helpers (CSRF included)

    protected ResultActions doGet(String url, RequestPostProcessor auth) throws Exception {
        return mvc.perform(get(url).with(auth));
    }

    protected ResultActions doSend(MockHttpServletRequestBuilder b, Object body, RequestPostProcessor auth) throws Exception {
        b.with(auth).with(csrf());
        if (body != null) b.contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body));
        return mvc.perform(b);
    }

    protected ResultActions doPost(String url, Object body, RequestPostProcessor auth) throws Exception { return doSend(post(url), body, auth); }
    protected ResultActions doPut(String url, Object body, RequestPostProcessor auth) throws Exception { return doSend(put(url), body, auth); }
    protected ResultActions doPatch(String url, Object body, RequestPostProcessor auth) throws Exception { return doSend(patch(url), body, auth); }
    protected ResultActions doDelete(String url, RequestPostProcessor auth) throws Exception { return doSend(delete(url), null, auth); }
}
