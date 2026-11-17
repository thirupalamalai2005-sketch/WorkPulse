package com.workpulse.controller;

import com.workpulse.dto.AttendanceDtos;
import com.workpulse.dto.PageResponse;
import com.workpulse.entity.CorrectionStatus;
import com.workpulse.service.AttendanceCorrectionService;
import com.workpulse.service.AttendanceService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final AttendanceService attendance;
    private final AttendanceCorrectionService corrections;

    public AttendanceController(AttendanceService attendance, AttendanceCorrectionService corrections) {
        this.attendance = attendance;
        this.corrections = corrections;
    }

    @PostMapping("/check-in")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AttendanceDtos.Entry> checkIn() {
        return ResponseEntity.status(HttpStatus.CREATED).body(attendance.checkIn());
    }

    @PostMapping("/check-out")
    @PreAuthorize("isAuthenticated()")
    public AttendanceDtos.Entry checkOut() {
        return attendance.checkOut();
    }

    @GetMapping("/today")
    @PreAuthorize("isAuthenticated()")
    public AttendanceDtos.TodayStatus today() {
        return attendance.today();
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public PageResponse<AttendanceDtos.Entry> history(@RequestParam(required = false) Long employeeId,
                                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "10") int size) {
        return attendance.history(employeeId, from, to, page, size);
    }

    @GetMapping("/summary")
    @PreAuthorize("isAuthenticated()")
    public AttendanceDtos.Summary summary(@RequestParam(required = false) Long employeeId,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return attendance.summary(employeeId, from, to);
    }

    @GetMapping("/roster")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public PageResponse<AttendanceDtos.RosterEntry> roster(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "10") int size) {
        return attendance.roster(date, page, size);
    }

    // ---- correction requests

    @PostMapping("/corrections")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AttendanceDtos.CorrectionResponse> submitCorrection(@Valid @RequestBody AttendanceDtos.CorrectionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(corrections.submit(request));
    }

    @GetMapping("/corrections/mine")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<AttendanceDtos.CorrectionResponse> myCorrections(@RequestParam(defaultValue = "0") int page,
                                                                         @RequestParam(defaultValue = "10") int size) {
        return corrections.mine(page, size);
    }

    @GetMapping("/corrections/review")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public PageResponse<AttendanceDtos.CorrectionResponse> reviewQueue(@RequestParam(required = false) CorrectionStatus status,
                                                                       @RequestParam(defaultValue = "0") int page,
                                                                       @RequestParam(defaultValue = "10") int size) {
        return corrections.forReview(status, page, size);
    }

    @PostMapping("/corrections/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public AttendanceDtos.CorrectionResponse approveCorrection(@PathVariable Long id,
                                                               @Valid @RequestBody(required = false) AttendanceDtos.ReviewRequest body) {
        return corrections.approve(id, body == null ? null : body.note());
    }

    @PostMapping("/corrections/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public AttendanceDtos.CorrectionResponse rejectCorrection(@PathVariable Long id,
                                                              @Valid @RequestBody(required = false) AttendanceDtos.ReviewRequest body) {
        return corrections.reject(id, body == null ? null : body.note());
    }
}
