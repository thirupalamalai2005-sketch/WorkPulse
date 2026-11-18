package com.workpulse.integration;

import com.workpulse.dto.LoginRequest;
import com.workpulse.entity.AttendanceRecord;
import com.workpulse.entity.Employee;
import com.workpulse.entity.Role;
import com.workpulse.repository.AttendanceRecordRepository;
import com.workpulse.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Notifications, audit log, dashboard, reports and calendar settings. */
class PlatformIntegrationTest extends AbstractIntegrationTest {

    @Autowired AttendanceRecordRepository records;

    private long insertNotification(Employee owner, String title) {
        jdbc.update("INSERT INTO notifications (user_id, type, title, message, is_read, created_at) VALUES (?, 'LEAVE', ?, 'msg', FALSE, CURRENT_TIMESTAMP)",
                owner.getUser().getUserId(), title);
        return jdbc.queryForObject("SELECT MAX(id) FROM notifications WHERE user_id = ?", Long.class, owner.getUser().getUserId());
    }

    private Map<String, Object> leave(String code, String from, String to) {
        Map<String, Object> m = new HashMap<>();
        m.put("leaveTypeId", leaveTypeId(code));
        m.put("startDate", from);
        m.put("endDate", to);
        m.put("reason", "Reason");
        return m;
    }

    // ---------------------------------------------------------------- notifications

    @Test
    void notificationsAreOwnedByTheirRecipient() throws Exception {
        Employee alice = newUser("alice@wp.com", Role.EMPLOYEE);
        Employee bob = newUser("bob@wp.com", Role.EMPLOYEE);
        long a1 = insertNotification(alice, "For Alice 1");
        insertNotification(alice, "For Alice 2");
        insertNotification(bob, "For Bob");

        doGet("/api/notifications", as(alice)).andExpect(jsonPath("$.totalElements", is(2)));
        doGet("/api/notifications/unread-count", as(alice)).andExpect(jsonPath("$.unread", is(2)));
        doGet("/api/notifications/unread-count", as(bob)).andExpect(jsonPath("$.unread", is(1)));

        doPost("/api/notifications/" + a1 + "/read", null, as(bob)).andExpect(status().isNotFound()); // not Bob's
        doGet("/api/notifications/unread-count", as(alice)).andExpect(jsonPath("$.unread", is(2)));

        doPost("/api/notifications/" + a1 + "/read", null, as(alice)).andExpect(status().isOk()).andExpect(jsonPath("$.read", is(true)));
        doGet("/api/notifications?unreadOnly=true", as(alice)).andExpect(jsonPath("$.totalElements", is(1)));
        // state is persisted server-side: a "fresh" request sees it
        doGet("/api/notifications/unread-count", as(alice)).andExpect(jsonPath("$.unread", is(1)));

        doPost("/api/notifications/read-all", null, as(alice)).andExpect(status().isOk());
        doGet("/api/notifications/unread-count", as(alice)).andExpect(jsonPath("$.unread", is(0)));
        doGet("/api/notifications/unread-count", as(bob)).andExpect(jsonPath("$.unread", is(1))); // Bob unaffected
    }

    // ---------------------------------------------------------------- audit log

    @Test
    void auditLogIsAdminOnlyFilterableAndFreeOfSecrets() throws Exception {
        Employee admin = newUser("admin@wp.com", Role.ADMIN);
        Employee manager = newUser("m@wp.com", Role.MANAGER);
        Employee emp = newUser("emp@wp.com", Role.EMPLOYEE);

        // a real login (failure + success) and an admin action that carries a password in its request
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(new LoginRequest("emp@wp.com", "Wrong@Password000")))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(new LoginRequest("emp@wp.com", PW)))).andExpect(status().isOk());
        doPost("/api/admin/users/" + emp.getUser().getUserId() + "/reset-password", Map.of("newPassword", "Reset@Password789"), as(admin)).andExpect(status().isOk());

        doGet("/api/audit-logs", as(emp)).andExpect(status().isForbidden());
        doGet("/api/audit-logs", as(manager)).andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/audit-logs")).andExpect(status().isUnauthorized());

        doGet("/api/audit-logs?action=LOGIN_FAILURE", as(admin)).andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].actorEmail", is("emp@wp.com")));
        doGet("/api/audit-logs?action=login_success&actor=EMP@", as(admin)).andExpect(jsonPath("$.totalElements", is(1)));
        doGet("/api/audit-logs?action=PASSWORD_RESET&resourceType=USER", as(admin)).andExpect(jsonPath("$.totalElements", is(1)));
        // audit rows carry the REAL timestamp (not the movable test clock), so filter by the real current date
        LocalDate realToday = LocalDate.now(clock.getZone());
        doGet("/api/audit-logs?from=" + realToday + "&to=" + realToday + "&size=2&page=0", as(admin))
                .andExpect(jsonPath("$.content", hasSize(2)));
        doGet("/api/audit-logs?from=" + realToday.plusDays(2), as(admin)).andExpect(jsonPath("$.totalElements", is(0)));

        String everything = doGet("/api/audit-logs?size=100", as(admin)).andReturn().getResponse().getContentAsString();
        assertFalse(everything.contains(PW), "audit log must never contain passwords");
        assertFalse(everything.contains("Reset@Password789"), "audit log must never contain passwords");
        assertFalse(everything.toLowerCase().contains("jsessionid"), "audit log must never contain session ids");

        // there is no way to alter audit records through the API
        doDelete("/api/audit-logs/1", as(admin)).andExpect(status().is4xxClientError());
        doPut("/api/audit-logs/1", Map.of(), as(admin)).andExpect(status().is4xxClientError());
    }

    @Test
    void businessActionsAreAudited() throws Exception {
        Employee admin = newUser("admin@wp.com", Role.ADMIN);
        Employee manager = newUser("m@wp.com", Role.MANAGER);
        Employee e = newUser("emp@wp.com", Role.EMPLOYEE, manager);
        long id = om.readTree(doPost("/api/leaves", leave("ANNUAL", "2026-03-09", "2026-03-09"), as(e)).andReturn().getResponse().getContentAsString()).get("id").asLong();
        doPost("/api/leaves/" + id + "/approve", null, as(manager)).andExpect(status().isOk());
        doGet("/api/audit-logs?action=LEAVE_APPROVED", as(admin)).andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].actorEmail", is("m@wp.com")))
                .andExpect(jsonPath("$.content[0].resourceId", is(String.valueOf(id))));
        doGet("/api/audit-logs?action=LEAVE_SUBMITTED", as(admin)).andExpect(jsonPath("$.totalElements", is(1)));
    }

    // ---------------------------------------------------------------- dashboard

    @Test
    void dashboardFiguresAreCalculatedFromPersistedData() throws Exception {
        Employee admin = newUser("admin@wp.com", Role.ADMIN);
        Employee manager = newUser("m@wp.com", Role.MANAGER);
        Employee e1 = newUser("e1@wp.com", Role.EMPLOYEE, manager);
        Employee e2 = newUser("e2@wp.com", Role.EMPLOYEE, manager);
        Employee e3 = newUser("e3@wp.com", Role.EMPLOYEE, manager);

        long sick = om.readTree(doPost("/api/leaves", leave("SICK", "2026-03-04", "2026-03-04"), as(e3)).andReturn().getResponse().getContentAsString()).get("id").asLong();
        doPost("/api/leaves/" + sick + "/approve", null, as(manager)).andExpect(status().isOk());
        doPost("/api/leaves", leave("ANNUAL", "2026-03-09", "2026-03-09"), as(e2)).andExpect(status().isCreated()); // stays pending

        clock.setLocal("2026-03-04T09:05");
        doPost("/api/attendance/check-in", null, as(e2)).andExpect(status().isCreated());
        clock.setLocal("2026-03-04T09:30");
        doPost("/api/attendance/check-in", null, as(e1)).andExpect(status().isCreated()); // late

        doGet("/api/dashboard/summary", as(admin))
                .andExpect(jsonPath("$.workforce.scope", is("ORGANIZATION")))
                .andExpect(jsonPath("$.workforce.totalEmployees", is(5)))
                .andExpect(jsonPath("$.workforce.presentToday", is(2)))
                .andExpect(jsonPath("$.workforce.lateToday", is(1)))
                .andExpect(jsonPath("$.workforce.onLeaveToday", is(1)))
                .andExpect(jsonPath("$.workforce.absentToday", is(2)))
                .andExpect(jsonPath("$.workforce.pendingLeaves", is(1)))
                .andExpect(jsonPath("$.workforce.attendanceRate", is(50)))
                .andExpect(jsonPath("$.recentActivity", not(empty())));

        doGet("/api/dashboard/summary", as(manager))
                .andExpect(jsonPath("$.workforce.scope", is("TEAM")))
                .andExpect(jsonPath("$.workforce.totalEmployees", is(3)))
                .andExpect(jsonPath("$.workforce.presentToday", is(2)))
                .andExpect(jsonPath("$.workforce.absentToday", is(0)))
                .andExpect(jsonPath("$.recentActivity", empty()));

        doGet("/api/dashboard/summary", as(e1))
                .andExpect(jsonPath("$.workforce", nullValue()))
                .andExpect(jsonPath("$.personal.todayState", is("CHECKED_IN")))
                .andExpect(jsonPath("$.personal.monthSummary.lateDays", is(1)))
                .andExpect(jsonPath("$.recentActivity", empty()));
        doGet("/api/dashboard/summary", as(e2)).andExpect(jsonPath("$.personal.myPendingLeaves", is(1)));
    }

    @Test
    void emptyDatabaseDashboardShowsZerosNotFabricatedData() throws Exception {
        Employee admin = newUser("admin@wp.com", Role.ADMIN);
        doGet("/api/dashboard/summary", as(admin)).andExpect(jsonPath("$.workforce.totalEmployees", is(1)))
                .andExpect(jsonPath("$.workforce.presentToday", is(0))).andExpect(jsonPath("$.workforce.pendingLeaves", is(0)));
    }

    // ---------------------------------------------------------------- reports

    private void insertRecord(Employee e, String date) {
        AttendanceRecord r = new AttendanceRecord();
        r.setEmployee(employeeRepository.findById(e.getId()).orElseThrow());
        r.setWorkDate(LocalDate.parse(date));
        r.setCheckInAt(java.time.LocalDateTime.parse(date + "T09:00").atZone(clock.getZone()).toInstant());
        r.setCheckOutAt(java.time.LocalDateTime.parse(date + "T17:00").atZone(clock.getZone()).toInstant());
        records.save(r);
    }

    @Test
    void reportsAreScopedAndExportableAsCsv() throws Exception {
        Employee admin = newUser("admin@wp.com", Role.ADMIN);
        Employee manager = newUser("m@wp.com", Role.MANAGER);
        Employee mine = newUser("mine@wp.com", Role.EMPLOYEE, manager);
        Employee notMine = newUser("notmine@wp.com", Role.EMPLOYEE);
        insertRecord(mine, "2026-03-02");
        insertRecord(notMine, "2026-03-02");

        doGet("/api/reports/attendance?from=2026-03-01&to=2026-03-04", as(admin)).andExpect(jsonPath("$.totalElements", is(2)));
        doGet("/api/reports/attendance?from=2026-03-01&to=2026-03-04", as(manager)).andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].employeeName", is("mine")));
        doGet("/api/reports/attendance?from=2026-03-01&to=2026-03-04&q=notmine", as(admin)).andExpect(jsonPath("$.totalElements", is(1)));
        doGet("/api/reports/attendance?from=2026-03-01&to=2026-03-04", as(mine)).andExpect(status().isForbidden());

        MvcResult csv = doGet("/api/reports/attendance.csv?from=2026-03-01&to=2026-03-04", as(admin)).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition", containsString("attachment"))).andReturn();
        String body = csv.getResponse().getContentAsString();
        assertTrue(body.contains("Employee Code"));
        assertTrue(body.contains("mine") && body.contains("notmine"));
        doGet("/api/reports/attendance.csv", as(mine)).andExpect(status().isForbidden());
        doGet("/api/reports/leaves.csv", as(admin)).andExpect(status().isOk());
        doGet("/api/audit-logs?action=REPORT_EXPORTED", as(admin)).andExpect(jsonPath("$.totalElements", is(2)));
    }

    @Test
    void csvExportNeutralisesSpreadsheetFormulas() throws Exception {
        Employee admin = newUser("admin@wp.com", Role.ADMIN);
        Employee evil = provisioning.provision("=HYPERLINK(\"http://evil\")", "evil@wp.com", PW, Role.EMPLOYEE, null, null, null, null, null);
        insertRecord(evil, "2026-03-02");
        String body = doGet("/api/reports/attendance.csv?from=2026-03-01&to=2026-03-04", as(admin)).andReturn().getResponse().getContentAsString();
        assertTrue(body.contains("\"'=HYPERLINK(\"\"http://evil\"\")\""), "formula must be prefixed with an apostrophe: " + body);
    }

    // ---------------------------------------------------------------- calendar & schedule

    @Test
    void scheduleAndHolidaysAreAdminManagedAndValidated() throws Exception {
        Employee admin = newUser("admin@wp.com", Role.ADMIN);
        Employee e = newUser("emp@wp.com", Role.EMPLOYEE);

        doGet("/api/schedule", as(e)).andExpect(jsonPath("$.startTime", is("09:00:00"))).andExpect(jsonPath("$.timezone", is("Asia/Kolkata")))
                .andExpect(jsonPath("$.workingDays", hasSize(5)));
        Map<String, Object> sched = Map.of("startTime", "10:00", "endTime", "19:00", "graceMinutes", 5, "workingDays", List.of("MON", "TUE", "WED", "THU", "FRI", "SAT"));
        doPut("/api/schedule", sched, as(e)).andExpect(status().isForbidden());
        doPut("/api/schedule", sched, as(admin)).andExpect(status().isOk()).andExpect(jsonPath("$.workingDays", hasSize(6)));
        doPut("/api/schedule", Map.of("startTime", "19:00", "endTime", "10:00", "graceMinutes", 5, "workingDays", List.of("MON")), as(admin)).andExpect(status().isBadRequest());
        doPut("/api/schedule", Map.of("startTime", "09:00", "endTime", "18:00", "graceMinutes", 5, "workingDays", List.of("FUNDAY")), as(admin)).andExpect(status().isBadRequest());

        // the new schedule drives lateness: 10:10 is on time now, 10:06 would be late (grace 5)
        clock.setLocal("2026-03-04T10:04");
        doPost("/api/attendance/check-in", null, as(e)).andExpect(jsonPath("$.late", is(false)));

        Map<String, Object> holiday = Map.of("date", "2026-08-15", "name", "Independence Day");
        doPost("/api/holidays", holiday, as(e)).andExpect(status().isForbidden());
        long hid = om.readTree(doPost("/api/holidays", holiday, as(admin)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        doPost("/api/holidays", holiday, as(admin)).andExpect(status().isConflict());
        doGet("/api/holidays?year=2026", as(e)).andExpect(jsonPath("$", hasSize(1)));
        doGet("/api/holidays?year=2027", as(e)).andExpect(jsonPath("$", hasSize(0)));
        doDelete("/api/holidays/" + hid, as(e)).andExpect(status().isForbidden());
        doDelete("/api/holidays/" + hid, as(admin)).andExpect(status().isNoContent());
        doDelete("/api/holidays/" + hid, as(admin)).andExpect(status().isNotFound());
    }

    @Test
    void unknownEndpointsAndMalformedInputGiveCleanErrors() throws Exception {
        Employee e = newUser("emp@wp.com", Role.EMPLOYEE);
        doGet("/api/does-not-exist", as(e)).andExpect(status().isNotFound());
        doPost("/api/leaves", Map.of("leaveTypeId", "abc"), as(e)).andExpect(status().isBadRequest());
        doGet("/api/leaves?status=BOGUS", as(e)).andExpect(status().isBadRequest());
        doGet("/api/leaves/999999", as(e)).andExpect(status().isNotFound());
        doGet("/api/attendance?from=not-a-date", as(e)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", not(containsString("Exception"))));
    }

    @Test
    void spoofedForwardedHeaderDoesNotChangeClientIpWhenStrategyNone() throws Exception {
        Employee admin = newUser("admin.audit@wp.com", Role.ADMIN);

        // Perform an audited action with a spoofed X-Forwarded-For header
        Map<String, Object> sched = Map.of("startTime", "09:00", "endTime", "18:00", "graceMinutes", 15, "workingDays", List.of("MON", "TUE", "WED", "THU", "FRI"));
        doSend(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/schedule").header("X-Forwarded-For", "203.0.113.195"), sched, as(admin))
                .andExpect(status().isOk());

        // Check audit log IP
        String lastIp = jdbc.queryForObject("SELECT ip_address FROM audit_logs WHERE action = 'SCHEDULE_UPDATED' ORDER BY id DESC LIMIT 1", String.class);
        assertNotEquals("203.0.113.195", lastIp, "Spoofed X-Forwarded-For must be ignored under forward-headers-strategy=none");
        assertEquals("127.0.0.1", lastIp, "Actual socket remote address (127.0.0.1) must be recorded in audit log");
    }
}
