package com.workpulse.controller;

import com.workpulse.dto.OrgDtos;
import com.workpulse.service.HolidayService;
import com.workpulse.service.ScheduleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/** Company holiday calendar and work schedule. Everyone reads; only admins change. */
@RestController
@RequestMapping("/api")
public class CalendarController {

    private final HolidayService holidays;
    private final ScheduleService schedule;
    private final Clock clock;

    public CalendarController(HolidayService holidays, ScheduleService schedule, Clock clock) {
        this.holidays = holidays;
        this.schedule = schedule;
        this.clock = clock;
    }

    @GetMapping("/holidays")
    @PreAuthorize("isAuthenticated()")
    public List<OrgDtos.HolidayResponse> listHolidays(@RequestParam(required = false) Integer year) {
        return holidays.listForYear(year != null ? year : LocalDate.now(clock).getYear());
    }

    @PostMapping("/holidays")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OrgDtos.HolidayResponse> createHoliday(@Valid @RequestBody OrgDtos.HolidayRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(holidays.create(request));
    }

    @DeleteMapping("/holidays/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteHoliday(@PathVariable Long id) {
        holidays.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/schedule")
    @PreAuthorize("isAuthenticated()")
    public OrgDtos.ScheduleResponse getSchedule() {
        return schedule.getResponse();
    }

    @PutMapping("/schedule")
    @PreAuthorize("hasRole('ADMIN')")
    public OrgDtos.ScheduleResponse updateSchedule(@Valid @RequestBody OrgDtos.ScheduleRequest request) {
        return schedule.update(request);
    }
}
