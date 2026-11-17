package com.workpulse.service;

import com.workpulse.entity.AuditLog;
import com.workpulse.entity.Employee;
import com.workpulse.entity.WorkSchedule;
import com.workpulse.exception.BadRequestException;
import com.workpulse.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/** Pure unit tests of package-level rules (no Spring context). */
class DomainRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 4);

    @Test
    void dateRangeDefaultsToCurrentMonthUntilToday() {
        LocalDate[] r = AttendanceService.normalizeRange(null, null, TODAY);
        assertEquals(LocalDate.of(2026, 3, 1), r[0]);
        assertEquals(TODAY, r[1]);
    }

    @Test
    void dateRangeRejectsInvertedAndOversizedRanges() {
        assertThrows(BadRequestException.class, () -> AttendanceService.normalizeRange(TODAY, TODAY.minusDays(1), TODAY));
        assertThrows(BadRequestException.class, () -> AttendanceService.normalizeRange(TODAY.minusDays(400), TODAY, TODAY));
        assertDoesNotThrow(() -> AttendanceService.normalizeRange(TODAY.minusDays(366), TODAY, TODAY));
    }

    @Test
    void employeeCodesArePaddedAndStable() {
        assertEquals("WP-0007", Employee.codeFor(7L));
        assertEquals("WP-12345", Employee.codeFor(12345L));
    }

    @Test
    void workScheduleParsesWorkingDays() {
        WorkSchedule s = new WorkSchedule();
        s.setWorkingDays("MON,WED,SAT");
        assertEquals(3, s.workingDaySet().size());
        assertTrue(s.workingDaySet().contains(DayOfWeek.SATURDAY));
        assertFalse(s.workingDaySet().contains(DayOfWeek.SUNDAY));
    }

    @Test
    void auditLogsCannotBeModifiedOrDeletedThroughCode() {
        boolean mutating = Arrays.stream(AuditLogRepository.class.getMethods()).map(Method::getName)
                .anyMatch(n -> n.startsWith("delete") || n.startsWith("remove") || n.startsWith("update"));
        assertFalse(mutating, "AuditLogRepository must expose no delete/update operations");
        boolean setters = Arrays.stream(AuditLog.class.getMethods()).map(Method::getName).anyMatch(n -> n.startsWith("set"));
        assertFalse(setters, "AuditLog must be immutable");
    }

    @Test
    void likeWildcardsAreEscaped() {
        assertEquals("100\\%\\_x", AuditService.escapeLike("100%_x"));
    }
}
