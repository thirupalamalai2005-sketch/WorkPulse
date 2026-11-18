package com.workpulse.service;

import com.workpulse.dto.MiscDtos;
import com.workpulse.dto.PageResponse;
import com.workpulse.entity.Notification;
import com.workpulse.exception.ResourceNotFoundException;
import com.workpulse.repository.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final NotificationRepository repository;
    private final AccessService access;

    public NotificationService(NotificationRepository repository, AccessService access) {
        this.repository = repository;
        this.access = access;
    }

    /** Creates a notification for one user. Joins the caller's transaction (all-or-nothing with the business action). */
    @Transactional
    public void notifyUser(Long userId, String type, String title, String message, String link) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(truncate(title, 150));
        n.setMessage(truncate(message, 500));
        n.setLink(link);
        repository.save(n);
    }

    @Transactional(readOnly = true)
    public PageResponse<MiscDtos.NotificationResponse> list(boolean unreadOnly, int page, int size) {
        Long userId = access.currentUser().getUserId();
        PageRequest pr = PageRequest.of(Math.max(page, 0), AuditService.clamp(size),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<Notification> result = unreadOnly ? repository.findByUserIdAndReadFlagFalse(userId, pr)
                                               : repository.findByUserId(userId, pr);
        return PageResponse.of(result, NotificationService::toResponse);
    }

    @Transactional(readOnly = true)
    public MiscDtos.UnreadCount unreadCount() {
        return new MiscDtos.UnreadCount(repository.countByUserIdAndReadFlagFalse(access.currentUser().getUserId()));
    }

    @Transactional
    public MiscDtos.NotificationResponse markRead(Long id) {
        Long userId = access.currentUser().getUserId();
        Notification n = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        n.setRead(true);
        return toResponse(repository.save(n));
    }

    @Transactional
    public int markAllRead() {
        return repository.markAllRead(access.currentUser().getUserId());
    }

    private static MiscDtos.NotificationResponse toResponse(Notification n) {
        return new MiscDtos.NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getMessage(),
                n.getLink(), n.isRead(), n.getCreatedAt());
    }

    private static String truncate(String s, int max) {
        return s == null ? "" : (s.length() > max ? s.substring(0, max) : s);
    }
}
