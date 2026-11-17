package com.workpulse.dto;

import com.workpulse.entity.Role;

/**
 * Authentication response DTO returned after successful login.
 */
public class AuthResponse {

    private Long userId;
    private String username;
    private String email;
    private Role role;
    private String message;

    public AuthResponse() {
    }

    public AuthResponse(Long userId, String username, String email, Role role, String message) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.role = role;
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

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
