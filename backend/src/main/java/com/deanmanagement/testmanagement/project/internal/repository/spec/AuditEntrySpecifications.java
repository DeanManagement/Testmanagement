package com.deanmanagement.testmanagement.project.internal.repository.spec;

import com.deanmanagement.testmanagement.project.internal.dto.audit.AuditFilter;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntry;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The activity query (PRD-046 §3.3), built only from the filters present: a {@code (:x IS NULL OR …)}
 * JPQL query would fail on PostgreSQL, which cannot type a null parameter.
 */
public final class AuditEntrySpecifications {

    private AuditEntrySpecifications() {
    }

    public static Specification<AuditEntry> build(UUID projectId, AuditFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("projectId"), projectId));
            if (filter.entityId() != null) {
                predicates.add(cb.or(cb.equal(root.get("entityId"), filter.entityId()),
                        cb.equal(root.get("parentEntityId"), filter.entityId())));
            }
            if (filter.entityTypes() != null && !filter.entityTypes().isEmpty()) {
                predicates.add(root.get("entityType").in(filter.entityTypes()));
            }
            if (filter.userIds() != null && !filter.userIds().isEmpty()) {
                predicates.add(root.get("userId").in(filter.userIds()));
            }
            if (filter.actions() != null && !filter.actions().isEmpty()) {
                predicates.add(root.get("action").in(filter.actions()));
            }
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), filter.to()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
