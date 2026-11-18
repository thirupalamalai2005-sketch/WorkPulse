package com.workpulse.repository;

import com.workpulse.entity.AuditLog;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.query.QueryUtils;

import java.util.List;

/**
 * Fragment implementation of {@link AuditLogSpecificationExecutor}.
 *
 * Spring Data JPA discovers this as the implementation for the
 * {@code AuditLogSpecificationExecutor} fragment because the class name is
 * {@code <FragmentInterfaceName>Impl}.  It provides only {@code findAll} and
 * {@code count} — intentionally no delete or update operations.
 */
public class AuditLogSpecificationExecutorImpl implements AuditLogSpecificationExecutor {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Page<AuditLog> findAll(Specification<AuditLog> spec, Pageable pageable) {
        CriteriaBuilder cb = em.getCriteriaBuilder();

        // Content query
        CriteriaQuery<AuditLog> query = cb.createQuery(AuditLog.class);
        Root<AuditLog> root = query.from(AuditLog.class);
        if (spec != null) {
            Predicate predicate = spec.toPredicate(root, query, cb);
            if (predicate != null) query.where(predicate);
        }
        if (pageable.getSort().isSorted()) {
            query.orderBy(QueryUtils.toOrders(pageable.getSort(), root, cb));
        }
        List<AuditLog> content = em.createQuery(query)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList();

        long total = count(spec);
        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public long count(Specification<AuditLog> spec) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<AuditLog> root = countQuery.from(AuditLog.class);
        countQuery.select(cb.count(root));
        if (spec != null) {
            Predicate predicate = spec.toPredicate(root, countQuery, cb);
            if (predicate != null) countQuery.where(predicate);
        }
        Long result = em.createQuery(countQuery).getSingleResult();
        return result == null ? 0L : result;
    }
}
