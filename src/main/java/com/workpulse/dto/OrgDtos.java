package com.workpulse.dto;

import jakarta.validation.constraints.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Departments, holidays and work schedule. */
public final class OrgDtos {
    private OrgDtos() { }

    public record DepartmentRequest(
            @NotBlank(message = "Name is required") @Size(max = 100, message = "Name must be at most 100 characters") String name,
            @Size(max = 255, message = "Description must be at most 255 characters") String description) { }

    public record DepartmentResponse(Long id, String name, String description, long employeeCount) { }

    public record HolidayRequest(
            @NotNull(message = "Date is required") LocalDate date,
            @NotBlank(message = "Name is required") @Size(max = 100, message = "Name must be at most 100 characters") String name) { }

    public record HolidayResponse(Long id, LocalDate date, String name) { }

    public record ScheduleRequest(
            @NotNull(message = "Start time is required") LocalTime startTime,
            @NotNull(message = "End time is required") LocalTime endTime,
            @Min(value = 0, message = "Grace must be 0-120 minutes") @Max(value = 120, message = "Grace must be 0-120 minutes") int graceMinutes,
            @NotEmpty(message = "Choose at least one working day") List<String> workingDays) { }

    public record ScheduleResponse(LocalTime startTime, LocalTime endTime, int graceMinutes,
                                   List<String> workingDays, String timezone, Instant serverNow) { }
}
