package com.workpulse.integration;

import com.workpulse.entity.Employee;
import com.workpulse.entity.Role;
import com.workpulse.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Clock: Wednesday 2026-03-04. Working days Mon-Fri. */
class LeaveIntegrationTest extends AbstractIntegrationTest {

    private Map<String, Object> leave(String code, String from, String to) {
        Map<String, Object> m = new HashMap<>();
        m.put("leaveTypeId", leaveTypeId(code));
        m.put("startDate", from);
        m.put("endDate", to);
        m.put("reason", "Personal reasons");
        return m;
    }

    private long submit(Employee e, String code, String from, String to) throws Exception {
        return om.readTree(doPost("/api/leaves", leave(code, from, to), as(e)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void daysCountOnlyWorkingDaysAndSkipHolidays() throws Exception {
        Employee admin = newUser("admin@wp.com", Role.ADMIN);
        Employee e = newUser("emp@wp.com", Role.EMPLOYEE);
        doPost("/api/leaves", leave("ANNUAL", "2026-03-09", "2026-03-13"), as(e)).andExpect(jsonPath("$.days", is(5)));
        doPost("/api/leaves", leave("ANNUAL", "2026-03-20", "2026-03-23"), as(e)).andExpect(jsonPath("$.days", is(2))); // Fri + Mon

        doPost("/api/holidays", Map.of("date", "2026-04-01", "name", "Company Day"), as(admin)).andExpect(status().isCreated());
        doPost("/api/leaves", leave("ANNUAL", "2026-03-30", "2026-04-03"), as(e)).andExpect(jsonPath("$.days", is(4)));

        doPost("/api/leaves", leave("ANNUAL", "2026-04-11", "2026-04-12"), as(e)).andExpect(status().isBadRequest()); // weekend only
    }

    @Test
    void balancesReflectPendingAndApprovedRequests() throws Exception {
        Employee manager = newUser("m@wp.com", Role.MANAGER);
        Employee e = newUser("emp@wp.com", Role.EMPLOYEE, manager);
        long id = submit(e, "ANNUAL", "2026-03-09", "2026-03-11");     // 3 days, pending
        submit(e, "CASUAL", "2026-03-16", "2026-03-16");               // 1 day, pending

        doGet("/api/leaves/balances", as(e))
                .andExpect(jsonPath("$[?(@.leaveTypeName=='Annual Leave')].pending", contains(3)))
                .andExpect(jsonPath("$[?(@.leaveTypeName=='Annual Leave')].remaining", contains(15)))
                .andExpect(jsonPath("$[?(@.leaveTypeName=='Casual Leave')].remaining", contains(5)));

        doPost("/api/leaves/" + id + "/approve", null, as(manager)).andExpect(status().isOk());
        doGet("/api/leaves/balances", as(e))
                .andExpect(jsonPath("$[?(@.leaveTypeName=='Annual Leave')].used", contains(3)))
                .andExpect(jsonPath("$[?(@.leaveTypeName=='Annual Leave')].pending", contains(0)))
                .andExpect(jsonPath("$[?(@.leaveTypeName=='Annual Leave')].remaining", contains(15)));
    }

    @Test
    void insufficientBalanceOverlapAndDateRulesAreEnforced() throws Exception {
        Employee e = newUser("emp@wp.com", Role.EMPLOYEE);
        doPost("/api/leaves", leave("CASUAL", "2026-03-09", "2026-03-17"), as(e)).andExpect(status().isBadRequest()); // 7 days > 6
        submit(e, "ANNUAL", "2026-03-09", "2026-03-11");
        doPost("/api/leaves", leave("CASUAL", "2026-03-11", "2026-03-12"), as(e)).andExpect(status().isConflict()); // overlap
        doPost("/api/leaves", leave("ANNUAL", "2026-03-12", "2026-03-11"), as(e)).andExpect(status().isBadRequest()); // end < start
        doPost("/api/leaves", leave("ANNUAL", "2026-03-02", "2026-03-03"), as(e)).andExpect(status().isBadRequest()); // past, not allowed
        doPost("/api/leaves", leave("SICK", "2026-03-02", "2026-03-03"), as(e)).andExpect(status().isCreated());      // sick may be back-dated
        doPost("/api/leaves", leave("SICK", "2026-01-02", "2026-01-02"), as(e)).andExpect(status().isBadRequest());   // > 30 days back
        doPost("/api/leaves", leave("ANNUAL", "2026-12-30", "2027-01-04"), as(e)).andExpect(status().isBadRequest()); // spans two years
        Map<String, Object> noReason = leave("ANNUAL", "2026-05-04", "2026-05-04");
        noReason.put("reason", "");
        doPost("/api/leaves", noReason, as(e)).andExpect(status().isBadRequest());
        Map<String, Object> badType = leave("ANNUAL", "2026-05-04", "2026-05-04");
        badType.put("leaveTypeId", 999999);
        doPost("/api/leaves", badType, as(e)).andExpect(status().isBadRequest());
    }

    @Test
    void onlyThePermittedReviewerCanDecide() throws Exception {
        Employee admin = newUser("admin@wp.com", Role.ADMIN);
        Employee manager = newUser("m@wp.com", Role.MANAGER);
        Employee otherManager = newUser("m2@wp.com", Role.MANAGER);
        Employee e = newUser("emp@wp.com", Role.EMPLOYEE, manager);
        Employee peer = newUser("peer@wp.com", Role.EMPLOYEE, manager);
        long id = submit(e, "ANNUAL", "2026-03-09", "2026-03-09");

        doPost("/api/leaves/" + id + "/approve", null, as(e)).andExpect(status().isForbidden());            // own request
        doPost("/api/leaves/" + id + "/approve", null, as(peer)).andExpect(status().isForbidden());         // no reviewer role
        doPost("/api/leaves/" + id + "/approve", null, as(otherManager)).andExpect(status().isForbidden()); // not their report
        doGet("/api/leaves/" + id, as(peer)).andExpect(status().isForbidden());                             // IDOR: cannot read others' requests
        doGet("/api/leaves/" + id, as(otherManager)).andExpect(status().isForbidden());
        doPost("/api/leaves/" + id + "/approve", Map.of("note", "Enjoy"), as(manager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("APPROVED")))
                .andExpect(jsonPath("$.reviewerName", is("m"))).andExpect(jsonPath("$.reviewedAt", notNullValue()));
        doPost("/api/leaves/" + id + "/reject", Map.of("note", "x"), as(admin)).andExpect(status().isConflict()); // already decided

        // admin cannot approve their own request either
        long adminLeave = submit(admin, "ANNUAL", "2026-03-10", "2026-03-10");
        doPost("/api/leaves/" + adminLeave + "/approve", null, as(admin)).andExpect(status().isForbidden());
    }

    @Test
    void rejectionNeedsANoteAndKeepsBalanceFree() throws Exception {
        Employee manager = newUser("m@wp.com", Role.MANAGER);
        Employee e = newUser("emp@wp.com", Role.EMPLOYEE, manager);
        long id = submit(e, "ANNUAL", "2026-03-09", "2026-03-10");
        doPost("/api/leaves/" + id + "/reject", Map.of("note", ""), as(manager)).andExpect(status().isBadRequest());
        doPost("/api/leaves/" + id + "/reject", Map.of("note", "Team deadline"), as(manager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("REJECTED")));
        doGet("/api/leaves/balances", as(e)).andExpect(jsonPath("$[?(@.leaveTypeName=='Annual Leave')].remaining", contains(18)));
    }

    @Test
    void ownersCanCancelPendingAndUpcomingApprovedLeaveOnly() throws Exception {
        Employee manager = newUser("m@wp.com", Role.MANAGER);
        Employee e = newUser("emp@wp.com", Role.EMPLOYEE, manager);
        Employee other = newUser("other@wp.com", Role.EMPLOYEE, manager);

        long pending = submit(e, "ANNUAL", "2026-03-09", "2026-03-10");
        doPost("/api/leaves/" + pending + "/cancel", null, as(other)).andExpect(status().isForbidden());
        doPost("/api/leaves/" + pending + "/cancel", null, as(e)).andExpect(status().isOk()).andExpect(jsonPath("$.status", is("CANCELLED")));
        doPost("/api/leaves/" + pending + "/cancel", null, as(e)).andExpect(status().isConflict());
        doGet("/api/leaves/balances", as(e)).andExpect(jsonPath("$[?(@.leaveTypeName=='Annual Leave')].remaining", contains(18)));

        long upcoming = submit(e, "ANNUAL", "2026-03-16", "2026-03-16");
        doPost("/api/leaves/" + upcoming + "/approve", null, as(manager)).andExpect(status().isOk());
        doPost("/api/leaves/" + upcoming + "/cancel", null, as(e)).andExpect(status().isOk());
        doGet("/api/notifications/unread-count", as(manager)).andExpect(jsonPath("$.unread", greaterThanOrEqualTo(1)));

        long started = submit(e, "SICK", "2026-03-04", "2026-03-05"); // starts today
        doPost("/api/leaves/" + started + "/approve", null, as(manager)).andExpect(status().isOk());
        doPost("/api/leaves/" + started + "/cancel", null, as(e)).andExpect(status().isConflict());
    }

    @Test
    void approvedLeaveFeedsRosterAndSummary() throws Exception {
        Employee manager = newUser("m@wp.com", Role.MANAGER);
        Employee e = newUser("emp@wp.com", Role.EMPLOYEE, manager);
        long id = submit(e, "SICK", "2026-03-03", "2026-03-04");
        doPost("/api/leaves/" + id + "/approve", null, as(manager)).andExpect(status().isOk());

        doGet("/api/attendance/roster?date=2026-03-03", as(manager)).andExpect(jsonPath("$.content[0].status", is("ON_LEAVE")));
        doGet("/api/attendance/summary?from=2026-03-02&to=2026-03-04", as(e))
                .andExpect(jsonPath("$.leaveDays", is(1)))   // Tue; Wed (today) is not counted yet
                .andExpect(jsonPath("$.absentDays", is(1))); // Mon without record
    }

    @Test
    void submitNotifiesTheManagerAndDecisionNotifiesTheEmployee() throws Exception {
        Employee manager = newUser("m@wp.com", Role.MANAGER);
        Employee e = newUser("emp@wp.com", Role.EMPLOYEE, manager);
        long id = submit(e, "ANNUAL", "2026-03-09", "2026-03-09");
        doGet("/api/notifications?unreadOnly=true", as(manager)).andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].type", is("LEAVE")));
        doPost("/api/leaves/" + id + "/approve", null, as(manager)).andExpect(status().isOk());
        doGet("/api/notifications?unreadOnly=true", as(e)).andExpect(jsonPath("$.content[0].title", is("Leave request approved")));
    }

    @Test
    void employeesWithoutManagerNotifyAdminsAndAdminsCanApprove() throws Exception {
        Employee admin = newUser("admin@wp.com", Role.ADMIN);
        Employee e = newUser("emp@wp.com", Role.EMPLOYEE);
        long id = submit(e, "ANNUAL", "2026-03-09", "2026-03-09");
        doGet("/api/notifications/unread-count", as(admin)).andExpect(jsonPath("$.unread", is(1)));
        doGet("/api/leaves/review?status=PENDING", as(admin)).andExpect(jsonPath("$.totalElements", is(1)));
        doGet("/api/leaves/review", as(e)).andExpect(status().isForbidden());
        doPost("/api/leaves/" + id + "/approve", null, as(admin)).andExpect(status().isOk());
    }

    @Test
    void adminCanChangeEntitlementsAndInactiveTypesAreHidden() throws Exception {
        Employee admin = newUser("admin@wp.com", Role.ADMIN);
        Employee e = newUser("emp@wp.com", Role.EMPLOYEE);
        long typeId = leaveTypeId("CASUAL");
        Map<String, Object> body = Map.of("name", "Casual Leave", "annualEntitlement", 12, "allowPastDates", false, "active", true);
        doPut("/api/leaves/types/" + typeId, body, as(e)).andExpect(status().isForbidden());
        doPut("/api/leaves/types/" + typeId, body, as(admin)).andExpect(status().isOk());
        doGet("/api/leaves/balances", as(e)).andExpect(jsonPath("$[?(@.leaveTypeName=='Casual Leave')].entitlement", contains(12)));

        doPut("/api/leaves/types/" + typeId, Map.of("name", "Casual Leave", "annualEntitlement", 12, "allowPastDates", false, "active", false), as(admin)).andExpect(status().isOk());
        doGet("/api/leaves/types", as(e)).andExpect(jsonPath("$[?(@.code=='CASUAL')]", hasSize(0)));
        doPost("/api/leaves", leave("CASUAL", "2026-03-09", "2026-03-09"), as(e)).andExpect(status().isBadRequest());
    }

    @Test
    void holidayAddedOrRemovedAfterRequestDoesNotCorruptExistingDayCountOrBalance() throws Exception {
        Employee admin = newUser("admin@wp.com", Role.ADMIN);
        Employee manager = newUser("m@wp.com", Role.MANAGER);
        Employee e = newUser("emp@wp.com", Role.EMPLOYEE, manager);

        // March 9 (Mon) - March 11 (Wed) = 3 working days
        long id = submit(e, "ANNUAL", "2026-03-09", "2026-03-11");
        doGet("/api/leaves/balances", as(e))
                .andExpect(jsonPath("$[?(@.leaveTypeName=='Annual Leave')].pending", contains(3)))
                .andExpect(jsonPath("$[?(@.leaveTypeName=='Annual Leave')].remaining", contains(15)));

        // Admin adds a holiday on March 10
        Map<String, Object> holiday = Map.of("date", "2026-03-10", "name", "Midweek Holiday");
        long hid = om.readTree(doPost("/api/holidays", holiday, as(admin)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        // Existing request dayCount and balance remain intact (immutable persisted snapshot)
        doGet("/api/leaves/" + id, as(e)).andExpect(jsonPath("$.days", is(3)));
        doGet("/api/leaves/balances", as(e))
                .andExpect(jsonPath("$[?(@.leaveTypeName=='Annual Leave')].pending", contains(3)))
                .andExpect(jsonPath("$[?(@.leaveTypeName=='Annual Leave')].remaining", contains(15)));

        // Manager approves; used remains 3, remaining remains 15
        doPost("/api/leaves/" + id + "/approve", null, as(manager)).andExpect(status().isOk());
        doGet("/api/leaves/balances", as(e))
                .andExpect(jsonPath("$[?(@.leaveTypeName=='Annual Leave')].used", contains(3)))
                .andExpect(jsonPath("$[?(@.leaveTypeName=='Annual Leave')].remaining", contains(15)));

        // Admin removes holiday; approved request still has 3 days
        doDelete("/api/holidays/" + hid, as(admin)).andExpect(status().isNoContent());
        doGet("/api/leaves/" + id, as(e)).andExpect(jsonPath("$.days", is(3)));
        doGet("/api/leaves/balances", as(e))
                .andExpect(jsonPath("$[?(@.leaveTypeName=='Annual Leave')].used", contains(3)))
                .andExpect(jsonPath("$[?(@.leaveTypeName=='Annual Leave')].remaining", contains(15)));
    }

    @Test
    void managerReassignedWhileRequestIsPendingTransfersApprovalRight() throws Exception {
        Employee admin = newUser("admin@wp.com", Role.ADMIN);
        Employee oldMgr = newUser("oldmgr@wp.com", Role.MANAGER);
        Employee newMgr = newUser("newmgr@wp.com", Role.MANAGER);
        Employee e = newUser("emp@wp.com", Role.EMPLOYEE, oldMgr);

        long id = submit(e, "ANNUAL", "2026-03-09", "2026-03-10");

        // Old manager can see and review initially
        doGet("/api/leaves/review?status=PENDING", as(oldMgr)).andExpect(jsonPath("$.totalElements", is(1)));
        doGet("/api/leaves/review?status=PENDING", as(newMgr)).andExpect(jsonPath("$.totalElements", is(0)));

        // Admin reassigns manager to newMgr
        Map<String, Object> update = Map.of(
                "username", e.getUser().getUsername(),
                "managerId", newMgr.getId(),
                "joinDate", "2025-01-01"
        );
        doPut("/api/employees/" + e.getId(), update, as(admin)).andExpect(status().isOk());

        // Old manager is now forbidden from approving; new manager has review permission
        doPost("/api/leaves/" + id + "/approve", null, as(oldMgr)).andExpect(status().isForbidden());
        doGet("/api/leaves/review?status=PENDING", as(oldMgr)).andExpect(jsonPath("$.totalElements", is(0)));
        doGet("/api/leaves/review?status=PENDING", as(newMgr)).andExpect(jsonPath("$.totalElements", is(1)));

        doPost("/api/leaves/" + id + "/approve", Map.of("note", "Approved by new manager"), as(newMgr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("APPROVED")))
                .andExpect(jsonPath("$.reviewerName", is("newmgr")));
    }

    @Test
    void deactivatedEmployeePendingRequestsCanBeReviewedByManager() throws Exception {
        Employee admin = newUser("admin@wp.com", Role.ADMIN);
        Employee manager = newUser("m@wp.com", Role.MANAGER);
        Employee e = newUser("emp@wp.com", Role.EMPLOYEE, manager);

        long id = submit(e, "ANNUAL", "2026-03-09", "2026-03-10");

        // Deactivate employee
        doPatch("/api/employees/" + e.getId() + "/status", Map.of("active", false), as(admin))
                .andExpect(status().isOk());

        // Deactivated employee cannot submit or cancel
        doPost("/api/leaves", leave("ANNUAL", "2026-03-16", "2026-03-17"), as(e))
                .andExpect(status().isForbidden());
        doPost("/api/leaves/" + id + "/cancel", null, as(e))
                .andExpect(status().isForbidden());

        // Manager can still review and reject the pending request
        doPost("/api/leaves/" + id + "/reject", Map.of("note", "Employee no longer with company"), as(manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("REJECTED")));
    }
}
