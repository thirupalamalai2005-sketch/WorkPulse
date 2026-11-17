package com.workpulse.controller;

import com.workpulse.dto.AttendanceDtos;
import com.workpulse.dto.LeaveDtos;
import com.workpulse.dto.PageResponse;
import com.workpulse.entity.LeaveStatus;
import com.workpulse.service.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/** Admin: whole company. Manager: direct reports only (enforced in ReportService). */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @GetMapping("/attendance")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public PageResponse<AttendanceDtos.Entry> attendance(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                         @RequestParam(required = false) Long departmentId,
                                                         @RequestParam(required = false) String q,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "20") int size) {
        return service.attendance(from, to, departmentId, q, page, size);
    }

    @GetMapping("/attendance.csv")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<byte[]> attendanceCsv(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                @RequestParam(required = false) Long departmentId,
                                                @RequestParam(required = false) String q) {
        return csv("attendance-report.csv", service.attendanceCsv(from, to, departmentId, q));
    }

    @GetMapping("/leaves")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public PageResponse<LeaveDtos.Response> leaves(@RequestParam(required = false) LeaveStatus status,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                   @RequestParam(required = false) Long departmentId,
                                                   @RequestParam(required = false) Long leaveTypeId,
                                                   @RequestParam(required = false) String q,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return service.leaves(status, from, to, departmentId, leaveTypeId, q, page, size);
    }

    @GetMapping("/leaves.csv")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<byte[]> leavesCsv(@RequestParam(required = false) LeaveStatus status,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                            @RequestParam(required = false) Long departmentId,
                                            @RequestParam(required = false) Long leaveTypeId,
                                            @RequestParam(required = false) String q) {
        return csv("leave-report.csv", service.leavesCsv(status, from, to, departmentId, leaveTypeId, q));
    }

    private ResponseEntity<byte[]> csv(String filename, String body) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(body.getBytes(StandardCharsets.UTF_8));
    }
}
