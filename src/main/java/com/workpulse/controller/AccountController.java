package com.workpulse.controller;

import com.workpulse.dto.AccountDtos;
import com.workpulse.dto.ApiResponse;
import com.workpulse.dto.EmployeeDtos;
import com.workpulse.service.AccountService;
import com.workpulse.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** The signed-in user's own profile and password. */
@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final AccountService accountService;
    private final AuthService authService;

    public AccountController(AccountService accountService, AuthService authService) {
        this.accountService = accountService;
        this.authService = authService;
    }

    @GetMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public EmployeeDtos.Response profile() {
        return accountService.profile();
    }

    @PutMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public EmployeeDtos.Response updateProfile(@Valid @RequestBody AccountDtos.UpdateProfileRequest request) {
        return accountService.updateProfile(request);
    }

    /** After a password change every session of this user (including this one) is ended. */
    @PostMapping("/password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<String>> changePassword(@Valid @RequestBody AccountDtos.ChangePasswordRequest request,
                                                              HttpServletRequest httpRequest) {
        accountService.changePassword(request);
        authService.logout(httpRequest);
        return ResponseEntity.ok(new ApiResponse<>(true, "Password changed. Please sign in again."));
    }
}
