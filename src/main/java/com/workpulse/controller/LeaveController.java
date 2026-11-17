package com.workpulse.controller;

import com.workpulse.dto.LeaveDtos;
import com.workpulse.dto.PageResponse;
import com.workpulse.entity.LeaveStatus;
import com.workpulse.service.LeaveService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leaves")
public class LeaveController {

    private final LeaveService service;

    public LeaveController(LeaveService service) {
        this.service = service;
    }

    @GetMapping("/types")
    @PreAuthorize("isAuthenticated()")
    public List<LeaveDtos.TypeResponse> types(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return service.types(includeInactive);
    }

    @PutMapping("/types/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public LeaveDtos.TypeResponse updateType(@PathVariable Long id, @Valid @RequestBody LeaveDtos.TypeUpdateRequest request) {
        return service.updateType(id, request);
    }

    @GetMapping("/balances")
    @PreAuthorize("isAuthenticated()")
    public List<LeaveDtos.Balance> balances(@RequestParam(required = false) Long employeeId,
                                            @RequestParam(required = false) Integer year) {
        return service.balances(employeeId, year);
    }

    /** The caller's own requests. */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public PageResponse<LeaveDtos.Response> mine(@RequestParam(required = false) LeaveStatus status,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "10") int size) {
        return service.mine(status, page, size);
    }

    /** Requests waiting for / decided by the caller as reviewer. */
    @GetMapping("/review")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public PageResponse<LeaveDtos.Response> review(@RequestParam(required = false) LeaveStatus status,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "10") int size) {
        return service.forReview(status, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public LeaveDtos.Response get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<LeaveDtos.Response> submit(@Valid @RequestBody LeaveDtos.SubmitRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.submit(request));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public LeaveDtos.Response cancel(@PathVariable Long id) {
        return service.cancel(id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public LeaveDtos.Response approve(@PathVariable Long id, @Valid @RequestBody(required = false) LeaveDtos.ReviewRequest body) {
        return service.approve(id, body == null ? null : body.note());
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public LeaveDtos.Response reject(@PathVariable Long id, @Valid @RequestBody(required = false) LeaveDtos.ReviewRequest body) {
        return service.reject(id, body == null ? null : body.note());
    }
}
