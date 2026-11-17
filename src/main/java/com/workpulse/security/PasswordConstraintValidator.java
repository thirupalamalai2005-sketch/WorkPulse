package com.workpulse.security;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Password policy: 12-128 chars, upper + lower + digit + special, no leading/trailing whitespace.
 * The rules live in the static {@link #violation(String)} so non-validation code (admin bootstrap,
 * unit tests) can reuse them.
 */
public class PasswordConstraintValidator implements ConstraintValidator<ValidPassword, String> {

    /** @return a human readable reason, or null when the password is acceptable. */
    public static String violation(String password) {
        if (password == null) return "Password is required";
        if (!password.equals(password.trim())) return "Password must not contain leading or trailing whitespace";
        if (password.trim().isEmpty()) return "Password must not be blank or contain only whitespace";
        if (password.length() < 12 || password.length() > 128) return "Password must be between 12 and 128 characters long";

        boolean hasUpper = false, hasLower = false, hasDigit = false, hasSpecial = false;
        for (char ch : password.toCharArray()) {
            if (Character.isUpperCase(ch)) hasUpper = true;
            else if (Character.isLowerCase(ch)) hasLower = true;
            else if (Character.isDigit(ch)) hasDigit = true;
            else if (!Character.isWhitespace(ch)) hasSpecial = true;
        }
        if (!hasUpper || !hasLower || !hasDigit || !hasSpecial) {
            return "Password must contain at least one uppercase letter, one lowercase letter, one digit, and one special character";
        }
        return null;
    }

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        String problem = violation(password);
        if (problem == null) return true;
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(problem).addConstraintViolation();
        return false;
    }
}
