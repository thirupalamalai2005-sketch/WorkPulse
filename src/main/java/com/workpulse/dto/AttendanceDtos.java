package com.workpulse.dto;

import jakarta.validation.constraints.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public final class AttendanceDtos {
    private AttendanceDtos() { }

    public record Entry(Long id, Long employeeId, String employeeCode, String employeeName, LocalDate workDate,
                         Instant checkInAt, Instant checkOutAt, boolean late, boolean missedCheckout,
                         Long workedMinutes, String source) { }

    /** state: NOT_CHECKED_IN, CHECKED_IN or CHECKED_OUT */
    public record TodayStatus(LocalDate date, String state, boolean workingDay, String dayNote, boolean onLeave,
                              Entry record, String timezone) { }

    public record Summary(LocalDate from, LocalDate to, int workingDays, int presentDays, int lateDays,
                          int missedCheckouts, int leaveDays, int absentDays, long totalWorkedMinutes) { }

    /** status: PRESENT, LATE, ON_LEAVE, ABSENT, HOLIDAY, WEEKLY_OFF, MISSED_CHECKOUT */
    public record RosterEntry(Long employeeId, String employeeCode, String employeeName, String departmentName,
                              String status, Instant checkInAt, Instant checkOutAt) { }

    public record CorrectionRequest(
            @NotNull(message = "Work date is required") LocalDate workDate,
            LocalTime checkInTime,
            LocalTime checkOutTime,
            @NotBlank(message = "Reason is required") @Size(max = 500, message = "Reason must be at most 500 characters") String reason) { }

    public record CorrectionResponse(Long id, Long employeeId, String employeeCode, String employeeName,
                                     LocalDate workDate, Instant requestedCheckIn, Instant requestedCheckOut,
                                     String reason, String status, String reviewerName, Instant reviewedAt,
                                     String reviewNote, Instant createdAt) { }

    public record ReviewRequest(@Size(max = 500, message = "Note must be at most 500 characters") String note) { }
}
