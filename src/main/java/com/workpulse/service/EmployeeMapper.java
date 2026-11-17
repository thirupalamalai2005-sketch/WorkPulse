package com.workpulse.service;

import com.workpulse.dto.EmployeeDtos;
import com.workpulse.entity.Employee;
import org.springframework.stereotype.Component;

@Component
public class EmployeeMapper {

    /** privileged = admin, the employee themself, or their manager: only they see phone, role and user id. */
    public EmployeeDtos.Response toResponse(Employee e, boolean privileged) {
        Employee m = e.getManager();
        return new EmployeeDtos.Response(
                e.getId(), e.getEmployeeCode(),
                privileged ? e.getUser().getUserId() : null,
                e.getUser().getUsername(), e.getUser().getEmail(),
                privileged ? e.getUser().getRole() : null,
                e.getDepartment() == null ? null : e.getDepartment().getId(),
                e.getDepartment() == null ? null : e.getDepartment().getName(),
                m == null ? null : m.getId(),
                m == null ? null : m.getUser().getUsername(),
                e.getDesignation(),
                privileged ? e.getPhone() : null,
                e.getJoinDate(), e.getStatus());
    }
}
