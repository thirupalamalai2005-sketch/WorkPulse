package com.workpulse.dto;

import com.workpulse.entity.EmployeeStatus;
import com.workpulse.entity.Role;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

public final class EmployeeDtos {
    private EmployeeDtos() { }

    /** phone, role and userId are null unless the viewer is an admin, the employee, or their manager. */
    public record Response(Long id, String employeeCode, Long userId, String name, String email, Role role,
                           Long departmentId, String departmentName, Long managerId, String managerName,
                           String designation, String phone, LocalDate joinDate, EmployeeStatus status) { }

    public record UpdateRequest(
            @NotBlank(message = "Name is required") @Size(min = 2, max = 50, message = "Name must be 2-50 characters") String username,
            Long departmentId,
            Long managerId,
            @Size(max = 100, message = "Designation must be at most 100 characters") String designation,
            @Pattern(regexp = "^$|^[+0-9()\\-\\s]{7,20}$", message = "Phone must be 7-20 digits/symbols") String phone,
            @NotNull(message = "Join date is required") LocalDate joinDate) { }

    public record RoleRequest(@NotNull(message = "Role is required") Role role) { }

    public record StatusRequest(@NotNull(message = "active flag is required") Boolean active) { }
}
