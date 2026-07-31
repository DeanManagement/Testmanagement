package com.deanmanagement.testmanagement.project.internal.repository.spec;

import com.deanmanagement.testmanagement.project.internal.dto.filter.TestRunListFilter;
import com.deanmanagement.testmanagement.project.internal.entity.TestRun;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds a {@link Specification} for the test-run list query from a {@link TestRunListFilter}.
 */
public final class TestRunSpecifications {

    private TestRunSpecifications() {
    }

    public static Specification<TestRun> build(UUID projectId, TestRunListFilter filter) {
        return (root, query, cb) -> {
            // Fetch the to-one relations the list projection reads, but only on the row
            // query — the paging count query must not carry fetch joins.
            if (TestRun.class.equals(query.getResultType())) {
                root.fetch("project", JoinType.LEFT);
                root.fetch("executor", JoinType.LEFT);
                root.fetch("completedBy", JoinType.LEFT);
                root.fetch("testPlan", JoinType.LEFT);
            }

            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("project").get("id"), projectId));

            if (filter.q() != null && !filter.q().isBlank()) {
                String like = "%" + filter.q().trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("key")), like)
                ));
            }

            if (filter.status() != null && !filter.status().isEmpty()) {
                predicates.add(root.get("status").in(filter.status()));
            }

            if (filter.testPlanId() != null) {
                predicates.add(cb.equal(root.get("testPlan").get("id"), filter.testPlanId()));
            }

            if (filter.executorId() != null) {
                predicates.add(cb.equal(root.get("executor").get("id"), filter.executorId()));
            }

            if (filter.startedAfter() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startTime"), filter.startedAfter()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
