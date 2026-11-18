package com.workpulse.controller;

import com.workpulse.dto.ApiResponse;
import com.workpulse.dto.MiscDtos;
import com.workpulse.dto.PageResponse;
import com.workpulse.service.NotificationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Every operation is scoped to the caller inside the service (ownership enforced server-side). */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public PageResponse<MiscDtos.NotificationResponse> list(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return service.list(unreadOnly, page, size);
    }

    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    public MiscDtos.UnreadCount unreadCount() {
        return service.unreadCount();
    }

    @PostMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public MiscDtos.NotificationResponse markRead(@PathVariable Long id) {
        return service.markRead(id);
    }

    @PostMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Integer> markAllRead() {
        int updated = service.markAllRead();
        return new ApiResponse<>(true, "Notifications marked as read", updated);
    }
}
