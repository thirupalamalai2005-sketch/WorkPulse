package com.workpulse.dto;

import jakarta.validation.constraints.*;

import java.time.Instant;
import java.time.LocalDate;

public final class LeaveDtos {
    private LeaveDtos() { }

    public record SubmitRequest(
            @NotNull(message = "Leave type is required") Long leaveTypeId,
            @NotNull(message = "Start date is required") LocalDate startDate,
            @NotNull(message = "End date is required") LocalDate endDate,
            @NotBlank(message = "Reason is required") @Size(max = 500, message = "Reason must be at most 500 characters") String reason) { }

    public record Response(Long id, Long employeeId, String employeeCode, String employeeName, Long leaveTypeId,
                           String leaveTypeName, LocalDate startDate, LocalDate endDate, int days, String reason,
                           String status, String reviewerName, Instant reviewedAt, String reviewNote,
                           Instant createdAt, boolean cancellable) { }

    public record TypeResponse(Long id, String code, String name, int annualEntitlement, boolean allowPastDates, boolean active) { }

    public record TypeUpdateRequest(
            @NotBlank(message = "Name is required") @Size(max = 60, message = "Name must be at most 60 characters") String name,
            @Min(value = 0, message = "Entitlement must be 0-365") @Max(value = 365, message = "Entitlement must be 0-365") int annualEntitlement,
            boolean allowPastDates,
            boolean active) { }

    public record Balance(Long leaveTypeId, String leaveTypeName, int entitlement, int used, int pending, int remaining) { }

    public record ReviewRequest(@Size(max = 500, message = "Note must be at most 500 characters") String note) { }
}
