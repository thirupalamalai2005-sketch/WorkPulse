package com.workpulse.controller;

import com.workpulse.dto.*;
import com.workpulse.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Public: register, login, config. Authenticated: logout, me. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @PreAuthorize("permitAll()")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    @PreAuthorize("permitAll()")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        return ResponseEntity.ok(authService.login(request, httpRequest, httpResponse));
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<String>> logout(HttpServletRequest httpRequest) {
        authService.logout(httpRequest);
        return ResponseEntity.ok(new ApiResponse<>(true, "Logged out successfully"));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserResponse> me(Authentication authentication) {
        return ResponseEntity.ok(authService.getCurrentUser(authentication));
    }

    /** Lets the login page know whether to show the "create account" link. */
    @GetMapping("/config")
    @PreAuthorize("permitAll()")
    public Map<String, Object> config() {
        return Map.of("registrationEnabled", authService.isRegistrationEnabled());
    }
}
