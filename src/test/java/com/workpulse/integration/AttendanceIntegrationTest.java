package com.workpulse.integration;

import com.workpulse.entity.AttendanceRecord;
import com.workpulse.entity.Employee;
import com.workpulse.entity.Role;
import com.workpulse.repository.AttendanceRecordRepository;
import com.workpulse.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Clock is fixed to Wednesday 2026-03-04 10:00 IST; work starts 09:00 with 15 minutes grace. */
class AttendanceIntegrationTest extends AbstractIntegrationTest {

    @Autowired AttendanceRecordRepository records;

    private void insertRecord(Employee e, String date, String in, String out, boolean late) {
        AttendanceRecord r = new AttendanceRecord();
        r.setEmployee(employeeRepository.findById(e.getId()).orElseThrow());
        r.setWorkDate(LocalDate.parse(date));
        ZoneId z = ZoneId.of("Asia/Kolkata");
        r.setCheckInAt(LocalDateTime.parse(date + "T" + in).atZone(z).toInstant());
        if (out != null) r.setCheckOutAt(LocalDateTime.parse(date + "T" + out).atZone(z).toInstant());
        r.setLate(late);
        records.save(r);
    }

    @Test
    void checkInOnTimeThenCheckOut() throws Exception {
        Employee e = newUser("emp@wp.com", Role.EMPLOYEE);
        clock.setLocal("2026-03-04T09:10");
        doPost("/api/attendance/check-in", null, as(e)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.late", is(false))).andExpect(jsonPath("$.workDate", is("2026-03-04")));
        doGet("/api/attendance/today", as(e)).andExpect(jsonPath("$.state", is("CHECKED_IN")));

        clock.setLocal("2026-03-04T18:05");
        doPost("/api/attendance/check-out", null, as(e)).andExpect(status().isOk())
                .andExpect(jsonPath("$.workedMinutes", is(535)));
        doGet("/api/attendance/today", as(e)).andExpect(jsonPath("$.state", is("CHECKED_OUT")));
    }

    @Test
    void arrivalAfterGraceIsLate() throws Exception {
        Employee e = newUser("emp@wp.com", Role.EMPLOYEE);
        clock.setLocal("2026-03-04T09:16");
        doPost("/api/attendance/check-in", null, as(e)).andExpect(jsonPath("$.late", is(true)));
    }

    @Test
    void invalidStateTransitionsAreRejected() throws Exception {
        Employee e = newUser("emp@wp.com", Role.EMPLOYEE);
        doPost("/api/attendance/check-out", null, as(e)).andExpect(status().isConflict());   // out before in
        doPost("/api/attendance/check-in", null, as(e)).andExpect(status().isCreated());
        doPost("/api/attendance/check-in", null, as(e)).andExpect(status().isConflict());    // duplicate in
        doPost("/api/attendance/check-out", null, as(e)).andExpect(status().isOk());
        doPost("/api/attendance/check-out", null, as(e)).andExpect(status().isConflict());   // duplicate out
    }

    @Test
    void checkInOnWeekendIsNeverLate() throws Exception {
        Employee e = newUser("emp@wp.com", Role.EMPLOYEE);
        clock.setLocal("2026-03-07T14:00"); // Saturday
        doPost("/api/attendance/check-in", null, as(e)).andExpect(jsonPath("$.late", is(false)));
        doGet("/api/attendance/today", as(e)).andExpect(jsonPath("$.workingDay", is(false)));
    }

    @Test
    void summaryIsCalculatedFromStoredRecordsAndApprovedLeave() throws Exception {
        Employee e = newUser("emp@wp.com", Role.EMPLOYEE);
        insertRecord(e, "2026-03-02", "09:30", "18:00", true);   // Mon, late, 510 min
        // Tue 03-03 has no record -> absent; Wed 03-04 is today -> not counted absent
        doGet("/api/attendance/summary?from=2026-03-02&to=2026-03-04", as(e))
                .andExpect(jsonPath("$.workingDays", is(3)))
                .andExpect(jsonPath("$.presentDays", is(1)))
                .andExpect(jsonPath("$.lateDays", is(1)))
                .andExpect(jsonPath("$.absentDays", is(1)))
                .andExpect(jsonPath("$.leaveDays", is(0)))
                .andExpect(jsonPath("$.totalWorkedMinutes", is(510)));
    }

    @Test
    void missedCheckoutIsFlaggedForPastDays() throws Exception {
        Employee e = newUser("emp@wp.com", Role.EMPLOYEE);
        insertRecord(e, "2026-03-03", "09:00", null, false);
        doGet("/api/attendance?from=2026-03-01&to=2026-03-04", as(e))
                .andExpect(jsonPath("$.content[0].missedCheckout", is(true)));
        doGet("/api/attendance/summary?from=2026-03-01&to=2026-03-04", as(e)).andExpect(jsonPath("$.missedCheckouts", is(1)));
    }

    @Test
    void historyIsPaginatedFilteredAndProtectedFromOtherEmployees() throws Exception {
        Employee manager = newUser("m@wp.com", Role.MANAGER);
        Employee e = newUser("emp@wp.com", Role.EMPLOYEE, manager);
        Employee other = newUser("other@wp.com", Role.EMPLOYEE);
        for (String d : new String[]{"2026-03-02", "2026-03-03"}) insertRecord(e, d, "09:00", "18:00", false);

        doGet("/api/attendance?from=2026-03-02&to=2026-03-04&size=1&page=0", as(e))
                .andExpect(jsonPath("$.totalElements", is(2))).andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].workDate", is("2026-03-03")));  // newest first
        doGet("/api/attendance?employeeId=" + e.getId() + "&from=2026-03-01&to=2026-03-04", as(other)).andExpect(status().isForbidden());
        doGet("/api/attendance?employeeId=" + e.getId() + "&from=2026-03-01&to=2026-03-04", as(manager)).andExpect(status().isOk());
        doGet("/api/attendance?from=2026-03-05&to=2026-03-01", as(e)).andExpect(status().isBadRequest());
        doGet("/api/attendance?from=2020-01-01&to=2026-03-04", as(e)).andExpect(status().isBadRequest()); // range too long
    }

    @Test
    void rosterIsScopedToTheManagersReports() throws Exception {
        Employee admin = newUser("admin@wp.com", Role.ADMIN);
        Employee manager = newUser("m@wp.com", Role.MANAGER);
        Employee mine = newUser("mine@wp.com", Role.EMPLOYEE, manager);
        newUser("notmine@wp.com", Role.EMPLOYEE);
        clock.setLocal("2026-03-04T09:05");
        doPost("/api/attendance/check-in", null, as(mine)).andExpect(status().isCreated());

        doGet("/api/attendance/roster?date=2026-03-04", as(manager)).andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].employeeName", is("mine"))).andExpect(jsonPath("$.content[0].status", is("PRESENT")));
        doGet("/api/attendance/roster?date=2026-03-04", as(admin)).andExpect(jsonPath("$.totalElements", is(4)));
        doGet("/api/attendance/roster?date=2026-03-03", as(manager)).andExpect(jsonPath("$.content[0].status", is("ABSENT")));
        doGet("/api/attendance/roster", as(mine)).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------- corrections

    private Map<String, Object> correction(String date, String in, String out, String reason) {
        Map<String, Object> m = new HashMap<>();
        m.put("workDate", date);
        m.put("checkInTime", in);
        m.put("checkOutTime", out);
        m.put("reason", reason);
        return m;
    }

    @Test
    void approvedCorrectionCreatesOrUpdatesTheRecordAndNotifiesTheEmployee() throws Exception {
        Employee manager = newUser("m@wp.com", Role.MANAGER);
        Employee e = newUser("emp@wp.com", Role.EMPLOYEE, manager);
        insertRecord(e, "2026-03-03", "09:00", null, false);

        long id = om.readTree(doPost("/api/attendance/corrections", correction("2026-03-03", null, "18:30", "Forgot to check out"), as(e))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        doPost("/api/attendance/corrections", correction("2026-03-03", null, "18:45", "again"), as(e)).andExpect(status().isConflict()); // already pending
        doGet("/api/notifications/unread-count", as(manager)).andExpect(jsonPath("$.unread", is(1)));

        doPost("/api/attendance/corrections/" + id + "/approve", Map.of("note", "ok"), as(manager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("APPROVED")));
        doGet("/api/attendance?from=2026-03-03&to=2026-03-03", as(e))
                .andExpect(jsonPath("$.content[0].missedCheckout", is(false)))
                .andExpect(jsonPath("$.content[0].source", is("CORRECTION")))
                .andExpect(jsonPath("$.content[0].workedMinutes", is(570)));
        doGet("/api/notifications/unread-count", as(e)).andExpect(jsonPath("$.unread", is(1)));
        doPost("/api/attendance/corrections/" + id + "/approve", null, as(manager)).andExpect(status().isConflict()); // already decided
    }

    @Test
    void correctionForDayWithoutRecordNeedsCheckInTime() throws Exception {
        Employee e = newUser("emp@wp.com", Role.EMPLOYEE);
        doPost("/api/attendance/corrections", correction("2026-03-02", null, "18:00", "x"), as(e)).andExpect(status().isBadRequest());
        doPost("/api/attendance/corrections", correction("2026-03-02", "09:00", "18:00", "Phone died"), as(e)).andExpect(status().isCreated());
    }

    @Test
    void correctionInputValidation() throws Exception {
        Employee e = newUser("emp@wp.com", Role.EMPLOYEE);
        doPost("/api/attendance/corrections", correction("2026-03-05", "09:00", null, "future"), as(e)).andExpect(status().isBadRequest());
        doPost("/api/attendance/corrections", correction("2026-01-01", "09:00", null, "too old"), as(e)).andExpect(status().isBadRequest());
        doPost("/api/attendance/corrections", correction("2026-03-02", "18:00", "09:00", "inverted"), as(e)).andExpect(status().isBadRequest());
        doPost("/api/attendance/corrections", correction("2026-03-02", null, null, "no times"), as(e)).andExpect(status().isBadRequest());
        doPost("/api/attendance/corrections", correction("2026-03-02", "09:00", null, " "), as(e)).andExpect(status().isBadRequest());
    }

    @Test
    void onlyTheRightReviewerMayDecideACorrection() throws Exception {
        Employee admin = newUser("admin@wp.com", Role.ADMIN);
        Employee manager = newUser("m@wp.com", Role.MANAGER);
        Employee otherManager = newUser("m2@wp.com", Role.MANAGER);
        Employee e = newUser("emp@wp.com", Role.EMPLOYEE, manager);
        long id = om.readTree(doPost("/api/attendance/corrections", correction("2026-03-02", "09:00", "18:00", "Phone died"), as(e))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        doPost("/api/attendance/corrections/" + id + "/approve", null, as(e)).andExpect(status().isForbidden());           // own request
        doPost("/api/attendance/corrections/" + id + "/approve", null, as(otherManager)).andExpect(status().isForbidden()); // not their report
        doGet("/api/attendance/corrections/review?status=PENDING", as(otherManager)).andExpect(jsonPath("$.totalElements", is(0)));
        doGet("/api/attendance/corrections/review?status=PENDING", as(manager)).andExpect(jsonPath("$.totalElements", is(1)));
        doPost("/api/attendance/corrections/" + id + "/reject", Map.of("note", " "), as(manager)).andExpect(status().isBadRequest()); // note required
        doPost("/api/attendance/corrections/" + id + "/reject", Map.of("note", "No evidence"), as(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("REJECTED")));
        assertTrue(records.findByEmployee_IdAndWorkDate(e.getId(), LocalDate.parse("2026-03-02")).isEmpty(), "rejected correction must not create a record");
    }

    @Test
    void midnightAndDateBoundariesFollowBusinessTimezone() throws Exception {
        Employee e1 = newUser("emp1@wp.com", Role.EMPLOYEE);
        Employee e2 = newUser("emp2@wp.com", Role.EMPLOYEE);

        // At 23:59:00 on 2026-03-03 in Asia/Kolkata (UTC is 2026-03-03T18:29:00Z)
        clock.setLocal("2026-03-03T23:59");
        doPost("/api/attendance/check-in", null, as(e1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.workDate", is("2026-03-03")));

        // At 00:01:00 on 2026-03-04 in Asia/Kolkata (UTC is 2026-03-03T18:31:00Z -> UTC date is March 3, but Business date is March 4)
        clock.setLocal("2026-03-04T00:01");
        doPost("/api/attendance/check-in", null, as(e2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.workDate", is("2026-03-04")));

        // Verify check-out on the business date works
        clock.setLocal("2026-03-04T00:05");
        doPost("/api/attendance/check-out", null, as(e2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workDate", is("2026-03-04")))
                .andExpect(jsonPath("$.checkOutAt", notNullValue()));
    }
}
