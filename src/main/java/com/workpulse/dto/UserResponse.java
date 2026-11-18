package com.workpulse.dto;

import com.workpulse.entity.Role;
import java.time.LocalDateTime;

/**
 * Safe user response DTO that excludes sensitive information like passwords.
 */
public class UserResponse {

    private Long userId;
    private String username;
    private String email;
    private Role role;
    private boolean enabled;
    private LocalDateTime createdAt;
    private String message;
    private Long employeeId;
    private String employeeCode;

    public UserResponse() {
    }

    public UserResponse(Long userId, String username, String email, Role role, boolean enabled, LocalDateTime createdAt, String message) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.role = role;
        this.enabled = enabled;
        this.createdAt = createdAt;
        this.message = message;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public void setEmployeeCode(String employeeCode) {
        this.employeeCode = employeeCode;
    }
}
