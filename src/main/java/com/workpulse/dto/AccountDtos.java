package com.workpulse.dto;

import com.workpulse.security.ValidPassword;
import jakarta.validation.constraints.*;

public final class AccountDtos {
    private AccountDtos() { }

    public record UpdateProfileRequest(
            @NotBlank(message = "Name is required") @Size(min = 2, max = 50, message = "Name must be 2-50 characters") String username,
            @Pattern(regexp = "^$|^[+0-9()\\-\\s]{7,20}$", message = "Phone must be 7-20 digits/symbols") String phone) { }

    public record ChangePasswordRequest(
            @NotBlank(message = "Current password is required") String currentPassword,
            @NotBlank(message = "New password is required") @ValidPassword String newPassword) { }

    public record ResetPasswordRequest(@NotBlank(message = "New password is required") @ValidPassword String newPassword) { }
}
