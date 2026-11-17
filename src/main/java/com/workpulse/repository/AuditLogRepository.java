package com.workpulse.repository;

import com.workpulse.entity.AuditLog;
import org.springframework.data.repository.Repository;

import java.util.List;

/**
 * Deliberately NOT a JpaRepository: only insert and read operations exist,
 * so no code can update or delete audit records.
 *
 * We extend our own {@link AuditLogSpecificationExecutor} (not Spring's
 * {@code JpaSpecificationExecutor}) because the latter declares a
 * {@code delete(Specification)} method that would violate the append-only
 * audit-log invariant checked by {@code DomainRulesTest}.  Spring Data JPA
 * provides the implementation at runtime via the custom fragment registered in
 * {@link AuditLogRepositoryImpl}.
 */
public interface AuditLogRepository
        extends Repository<AuditLog, Long>, AuditLogSpecificationExecutor {
    AuditLog save(AuditLog log);
    List<AuditLog> findTop5ByOrderByIdDesc();
}

