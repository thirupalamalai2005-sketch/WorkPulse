package com.workpulse.integration;

import com.workpulse.dto.AdminCreateUserRequest;
import com.workpulse.dto.LoginRequest;
import com.workpulse.entity.Employee;
import com.workpulse.entity.Role;
import com.workpulse.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class EmployeeIntegrationTest extends AbstractIntegrationTest {

    private Map<String, Object> update(Employee e, Long deptId, Long managerId, String phone) {
        Map<String, Object> m = new HashMap<>();
        m.put("username", e.getUser().getUsername());
        m.put("departmentId", deptId);
        m.put("managerId", managerId);
        m.put("designation", "Engineer");
        m.put("phone", phone);
        m.put("joinDate", "2025-01-01");
        return m;
    }

    @Test
    void adminCreatesEmployeeWithDepartmentAndManager() throws Exception {
        Employee admin = newUser("a@wp.com", Role.ADMIN);
        Employee manager = newUser("m@wp.com", Role.MANAGER);
        String dept = doPost("/api/departments", Map.of("name", "Engineering"), as(admin)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long deptId = om.readTree(dept).get("id").asLong();

        AdminCreateUserRequest req = new AdminCreateUserRequest("New Hire", "hire@wp.com", PW, Role.EMPLOYEE);
        req.setDepartmentId(deptId);
        req.setManagerId(manager.getId());
        req.setDesignation("Developer");
        doPost("/api/admin/users", req, as(admin)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.employeeCode", startsWith("WP-")));

        Employee created = employeeRepository.findByUser_Email("hire@wp.com").orElseThrow();
        doGet("/api/employees/" + created.getId(), as(admin))
                .andExpect(jsonPath("$.departmentName", is("Engineering")))
                .andExpect(jsonPath("$.managerName", is("m")))
                .andExpect(jsonPath("$.designation", is("Developer")));
    }

    @Test
    void adminCreateIsForbiddenForNonAdminsAndRejectsDuplicates() throws Exception {
        Employee admin = newUser("a@wp.com", Role.ADMIN);
        Employee emp = newUser("e@wp.com", Role.EMPLOYEE);
        Employee mgr = newUser("m@wp.com", Role.MANAGER);
        AdminCreateUserRequest req = new AdminCreateUserRequest("Xavier", "x@wp.com", PW, Role.EMPLOYEE);
        doPost("/api/admin/users", req, as(emp)).andExpect(status().isForbidden());
        doPost("/api/admin/users", req, as(mgr)).andExpect(status().isForbidden());
        doPost("/api/admin/users", req, as(admin)).andExpect(status().isCreated());
        doPost("/api/admin/users", req, as(admin)).andExpect(status().isConflict());
    }

    @Test
    void listSupportsPaginationSearchAndFilters() throws Exception {
        Employee admin = newUser("admin@wp.com", Role.ADMIN);
        for (int i = 1; i <= 12; i++) newUser(String.format("worker%02d@wp.com", i), Role.EMPLOYEE);

        doGet("/api/employees?size=5&page=1&sort=name&dir=asc", as(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(5)))
                .andExpect(jsonPath("$.totalElements", is(13)))
                .andExpect(jsonPath("$.totalPages", is(3)));
        doGet("/api/employees?q=worker07", as(admin)).andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].email", is("worker07@wp.com")));
        doGet("/api/employees?q=%25", as(admin)).andExpect(jsonPath("$.totalElements", is(0))); // LIKE wildcard is escaped
        doGet("/api/employees?status=INACTIVE", as(admin)).andExpect(jsonPath("$.totalElements", is(0)));
        doGet("/api/employees?sort=password", as(admin)).andExpect(status().isOk()); // unknown sort key falls back safely
    }

    @Test
    void personalDataIsRestrictedToOwnerManagerAndAdmin() throws Exception {
        Employee admin = newUser("admin@wp.com", Role.ADMIN);
        Employee manager = newUser("manager@wp.com", Role.MANAGER);
        Employee alice = newUser("alice@wp.com", Role.EMPLOYEE, manager);
        Employee bob = newUser("bob@wp.com", Role.EMPLOYEE);

        String url = "/api/employees/" + alice.getId();
        doGet(url, as(bob)).andExpect(status().isOk()).andExpect(jsonPath("$.phone", nullValue()))
                .andExpect(jsonPath("$.role", nullValue())).andExpect(jsonPath("$.name", is("alice")));
        doGet(url, as(alice)).andExpect(jsonPath("$.phone", is("+91 98765 43210")));
        doGet(url, as(manager)).andExpect(jsonPath("$.phone", is("+91 98765 43210")));
        doGet(url, as(admin)).andExpect(jsonPath("$.phone", is("+91 98765 43210")));
        // the directory list applies the same rule
        doGet("/api/employees?q=alice", as(bob)).andExpect(jsonPath("$.content[0].phone", nullValue()));
    }

    @Test
    void onlyAdminsMayEditEmployees() throws Exception {
        Employee admin = newUser("admin@wp.com", Role.ADMIN);
        Employee alice = newUser("alice@wp.com", Role.EMPLOYEE);
        doPut("/api/employees/" + alice.getId(), update(alice, null, null, "+91 11111 22222"), as(alice)).andExpect(status().isForbidden());
        doPut("/api/employees/" + alice.getId(), update(alice, null, null, "+91 11111 22222"), as(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.phone", is("+91 11111 22222")));
        doPut("/api/employees/" + alice.getId(), update(alice, null, null, "not-a-phone!"), as(admin)).andExpect(status().isBadRequest());
        doPut("/api/employees/999999", update(alice, null, null, null), as(admin)).andExpect(status().isNotFound());
    }

    @Test
    void managerAssignmentIsValidated() throws Exception {
        Employee admin = newUser("admin@wp.com", Role.ADMIN);
        Employee plain = newUser("plain@wp.com", Role.EMPLOYEE);
        Employee m1 = newUser("m1@wp.com", Role.MANAGER);
        Employee m2 = newUser("m2@wp.com", Role.MANAGER);

        doPut("/api/employees/" + m1.getId(), update(m1, null, plain.getId(), null), as(admin)).andExpect(status().isBadRequest()); // not a manager role
        doPut("/api/employees/" + m1.getId(), update(m1, null, m1.getId(), null), as(admin)).andExpect(status().isBadRequest());     // self
        doPut("/api/employees/" + m2.getId(), update(m2, null, m1.getId(), null), as(admin)).andExpect(status().isOk());
        doPut("/api/employees/" + m1.getId(), update(m1, null, m2.getId(), null), as(admin)).andExpect(status().isBadRequest());     // loop
    }

    @Test
    void deactivationBlocksLoginAndHidesEmployeeFromDirectory() throws Exception {
        Employee admin = newUser("admin@wp.com", Role.ADMIN);
        Employee alice = newUser("alice@wp.com", Role.EMPLOYEE);
        Employee bob = newUser("bob@wp.com", Role.EMPLOYEE);

        doPatch("/api/employees/" + alice.getId() + "/status", Map.of("active", false), as(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("INACTIVE")));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(new LoginRequest("alice@wp.com", PW)))).andExpect(status().isUnauthorized());
        doGet("/api/employees?q=alice", as(bob)).andExpect(jsonPath("$.totalElements", is(0)));
        doGet("/api/employees?q=alice&status=INACTIVE", as(admin)).andExpect(jsonPath("$.totalElements", is(1)));

        doPatch("/api/employees/" + alice.getId() + "/status", Map.of("active", true), as(admin)).andExpect(jsonPath("$.status", is("ACTIVE")));
        doPatch("/api/employees/" + alice.getId() + "/status", Map.of("active", false), as(bob)).andExpect(status().isForbidden());
    }

    @Test
    void safeguardsPreventLockoutAndOrphanedReports() throws Exception {
        Employee admin = newUser("admin@wp.com", Role.ADMIN);
        Employee manager = newUser("manager@wp.com", Role.MANAGER);
        newUser("report@wp.com", Role.EMPLOYEE, manager);

        doPatch("/api/employees/" + admin.getId() + "/status", Map.of("active", false), as(admin)).andExpect(status().isConflict());
        doPatch("/api/employees/" + admin.getId() + "/role", Map.of("role", "EMPLOYEE"), as(admin)).andExpect(status().isConflict());
        doPatch("/api/employees/" + manager.getId() + "/status", Map.of("active", false), as(admin)).andExpect(status().isConflict());
        doPatch("/api/employees/" + manager.getId() + "/role", Map.of("role", "EMPLOYEE"), as(admin)).andExpect(status().isConflict());
    }

    @Test
    void adminCanChangeRoleAndResetPassword() throws Exception {
        Employee admin = newUser("admin@wp.com", Role.ADMIN);
        Employee alice = newUser("alice@wp.com", Role.EMPLOYEE);
        doPatch("/api/employees/" + alice.getId() + "/role", Map.of("role", "MANAGER"), as(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role", is("MANAGER")));

        String newPw = "Reset@Password789";
        doPost("/api/admin/users/" + alice.getUser().getUserId() + "/reset-password", Map.of("newPassword", newPw), as(admin)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(new LoginRequest("alice@wp.com", newPw)))).andExpect(status().isOk());
        doPost("/api/admin/users/" + alice.getUser().getUserId() + "/reset-password", Map.of("newPassword", "weak"), as(admin)).andExpect(status().isBadRequest());
    }

    @Test
    void departmentsCannotBeDeletedWhileInUseUnlessEmployeesAreReassigned() throws Exception {
        Employee admin = newUser("admin@wp.com", Role.ADMIN);
        Employee alice = newUser("alice@wp.com", Role.EMPLOYEE);
        long d1 = om.readTree(doPost("/api/departments", Map.of("name", "Ops"), as(admin)).andReturn().getResponse().getContentAsString()).get("id").asLong();
        long d2 = om.readTree(doPost("/api/departments", Map.of("name", "Support"), as(admin)).andReturn().getResponse().getContentAsString()).get("id").asLong();
        doPost("/api/departments", Map.of("name", "ops"), as(admin)).andExpect(status().isConflict()); // case-insensitive duplicate
        doPut("/api/employees/" + alice.getId(), update(alice, d1, null, null), as(admin)).andExpect(status().isOk());

        doDelete("/api/departments/" + d1, as(admin)).andExpect(status().isConflict());
        doDelete("/api/departments/" + d1 + "?reassignTo=" + d2, as(admin)).andExpect(status().isNoContent());
        doGet("/api/employees/" + alice.getId(), as(admin)).andExpect(jsonPath("$.departmentName", is("Support")));
        doDelete("/api/departments/" + d2, as(alice)).andExpect(status().isForbidden());
    }
}
