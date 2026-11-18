package com.workpulse.controller;

import com.workpulse.dto.MiscDtos;
import com.workpulse.dto.PageResponse;
import com.workpulse.service.AuditService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.LocalDate;

/** Read-only, administrator-only. There is intentionally no POST/PUT/DELETE. */
@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

    private final AuditService audit;
    private final Clock clock;

    public AuditLogController(AuditService audit, Clock clock) {
        this.audit = audit;
        this.clock = clock;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<MiscDtos.AuditLogResponse> search(@RequestParam(required = false) String action,
                                                          @RequestParam(required = false) String actor,
                                                          @RequestParam(required = false) String resourceType,
                                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        var zone = clock.getZone();
        return audit.search(action, actor, resourceType,
                from == null ? null : from.atStartOfDay(zone).toInstant(),
                to == null ? null : to.plusDays(1).atStartOfDay(zone).toInstant(), // 'to' day is inclusive
                page, size);
    }
}
