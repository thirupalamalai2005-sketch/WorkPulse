package com.workpulse.controller;

import com.workpulse.dto.EmployeeDtos;
import com.workpulse.dto.PageResponse;
import com.workpulse.entity.EmployeeStatus;
import com.workpulse.service.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeService service;

    public EmployeeController(EmployeeService service) {
        this.service = service;
    }

    /** Directory. Everyone may list colleagues; private fields are filtered per viewer inside the service. */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public PageResponse<EmployeeDtos.Response> list(@RequestParam(required = false) String q,
                                                    @RequestParam(required = false) Long departmentId,
                                                    @RequestParam(required = false) EmployeeStatus status,
                                                    @RequestParam(defaultValue = "name") String sort,
                                                    @RequestParam(defaultValue = "asc") String dir,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "10") int size) {
        return service.list(q, departmentId, status, sort, dir, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public EmployeeDtos.Response get(@PathVariable Long id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public EmployeeDtos.Response update(@PathVariable Long id, @Valid @RequestBody EmployeeDtos.UpdateRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public EmployeeDtos.Response setStatus(@PathVariable Long id, @Valid @RequestBody EmployeeDtos.StatusRequest request) {
        return service.setActive(id, request.active());
    }

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public EmployeeDtos.Response changeRole(@PathVariable Long id, @Valid @RequestBody EmployeeDtos.RoleRequest request) {
        return service.changeRole(id, request.role());
    }
}
