package com.workpulse.controller;

import com.workpulse.dto.AccountDtos;
import com.workpulse.dto.AdminCreateUserRequest;
import com.workpulse.dto.ApiResponse;
import com.workpulse.dto.UserResponse;
import com.workpulse.service.AccountService;
import com.workpulse.service.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Administrator-only account provisioning. */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final EmployeeService employeeService;
    private final AccountService accountService;

    public AdminUserController(EmployeeService employeeService, AccountService accountService) {
        this.employeeService = employeeService;
        this.accountService = accountService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody AdminCreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(employeeService.adminCreate(request));
    }

    @PostMapping("/{userId}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<String> resetPassword(@PathVariable Long userId,
                                             @Valid @RequestBody AccountDtos.ResetPasswordRequest request) {
        accountService.adminResetPassword(userId, request);
        return new ApiResponse<>(true, "Password reset. The user's existing sessions were ended.");
    }
}
