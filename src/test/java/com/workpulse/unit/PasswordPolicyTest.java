package com.workpulse.unit;

import com.workpulse.security.PasswordConstraintValidator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordPolicyTest {

    @Test
    void acceptsStrongPasswords() {
        assertNull(PasswordConstraintValidator.violation("Strong@Password123"));
        assertNull(PasswordConstraintValidator.violation("Ab1!" + "x".repeat(124))); // exactly 128
    }

    @Test
    void rejectsWeakPasswords() {
        assertNotNull(PasswordConstraintValidator.violation(null));
        assertNotNull(PasswordConstraintValidator.violation("Short1!"));
        assertNotNull(PasswordConstraintValidator.violation("alllowercase123!"));
        assertNotNull(PasswordConstraintValidator.violation("ALLUPPERCASE123!"));
        assertNotNull(PasswordConstraintValidator.violation("NoDigitsHere!!!!"));
        assertNotNull(PasswordConstraintValidator.violation("NoSpecial12345Ab"));
        assertNotNull(PasswordConstraintValidator.violation(" Strong@Password123"));
        assertNotNull(PasswordConstraintValidator.violation("Ab1!" + "x".repeat(125))); // 129
        assertNotNull(PasswordConstraintValidator.violation("            "));
    }
}
