package com.workpulse.repository;

import com.workpulse.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

/**
 * Restricted specification-query interface for AuditLog.
 *
 * We deliberately do NOT extend {@link org.springframework.data.jpa.repository.JpaSpecificationExecutor}
 * because that interface declares {@code delete(Specification)} which would violate the
 * append-only invariant of the audit trail. This interface exposes only the read operations
 * that {@link com.workpulse.service.AuditService} requires.
 */
public interface AuditLogSpecificationExecutor {
    Page<AuditLog> findAll(Specification<AuditLog> spec, Pageable pageable);
    long count(Specification<AuditLog> spec);
}
