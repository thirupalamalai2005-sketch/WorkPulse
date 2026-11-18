package com.workpulse.service;

import com.workpulse.dto.MiscDtos;
import com.workpulse.dto.PageResponse;
import com.workpulse.entity.AuditLog;
import com.workpulse.repository.AuditLogRepository;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes and reads the audit trail. Callers must only pass safe metadata - never passwords,
 * tokens or session ids.
 */
@Service
public class AuditService {

    private static final Logger logger = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    /** Own transaction: the audit row is kept even if the surrounding business transaction rolls back. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String actorEmail, String action, String resourceType, String resourceId, String details) {
        try {
            String safeDetails = details == null ? null : (details.length() > 1000 ? details.substring(0, 1000) : details);
            repository.save(new AuditLog(actorEmail, action, resourceType, resourceId, safeDetails, currentIp()));
        } catch (RuntimeException ex) {
            // Auditing problems must not break the user's request, but they must be visible to operators.
            logger.error("Failed to write audit record for action {}", action, ex);
        }
    }

    /** Logs with the currently authenticated user as actor. */
    public void logCurrent(String action, String resourceType, Object resourceId, String details) {
        String actor = null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            actor = auth.getName();
        }
        log(actor, action, resourceType, resourceId == null ? null : String.valueOf(resourceId), details);
    }

    @Transactional(readOnly = true)
    public PageResponse<MiscDtos.AuditLogResponse> search(String action, String actor, String resourceType,
                                                         Instant from, Instant to, int page, int size) {
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (action != null && !action.isBlank()) p.add(cb.equal(root.get("action"), action.trim().toUpperCase()));
            if (actor != null && !actor.isBlank())
                p.add(cb.like(cb.lower(root.get("actorEmail")), "%" + escapeLike(actor.trim().toLowerCase()) + "%", '\\'));
            if (resourceType != null && !resourceType.isBlank()) p.add(cb.equal(root.get("resourceType"), resourceType.trim().toUpperCase()));
            if (from != null) p.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            if (to != null) p.add(cb.lessThan(root.get("createdAt"), to));
            return cb.and(p.toArray(new Predicate[0]));
        };
        var result = repository.findAll(spec, PageRequest.of(Math.max(page, 0), clamp(size),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))));
        return PageResponse.of(result, AuditService::toResponse);
    }

    @Transactional(readOnly = true)
    public List<MiscDtos.AuditLogResponse> recent() {
        return repository.findTop5ByOrderByIdDesc().stream().map(AuditService::toResponse).toList();
    }

    static MiscDtos.AuditLogResponse toResponse(AuditLog a) {
        return new MiscDtos.AuditLogResponse(a.getId(), a.getActorEmail(), a.getAction(), a.getResourceType(),
                a.getResourceId(), a.getDetails(), a.getIpAddress(), a.getCreatedAt());
    }

    static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    static int clamp(int size) {
        return Math.min(Math.max(size, 1), 100);
    }

    private static String currentIp() {
        try {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
                return attrs.getRequest().getRemoteAddr();
            }
        } catch (RuntimeException ignored) {
            // no request bound (e.g. startup tasks)
        }
        return null;
    }
}
