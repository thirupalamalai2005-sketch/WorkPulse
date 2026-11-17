package com.workpulse.security;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Custom validation annotation for enterprise password policy:
 * - 12 to 128 characters
 * - At least one uppercase letter
 * - At least one lowercase letter
 * - At least one digit
 * - At least one special character
 * - No leading or trailing whitespace
 * - Not composed entirely of whitespace
 */
@Documented
@Constraint(validatedBy = PasswordConstraintValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPassword {

    String message() default "Password must be 12-128 characters long, contain at least one uppercase letter, one lowercase letter, one digit, one special character, and cannot have leading or trailing whitespace";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
