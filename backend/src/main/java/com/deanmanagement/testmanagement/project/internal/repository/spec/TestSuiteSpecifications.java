package com.deanmanagement.testmanagement.project.internal.repository.spec;

import com.deanmanagement.testmanagement.project.internal.dto.filter.TestSuiteListFilter;
import com.deanmanagement.testmanagement.project.internal.entity.TestSuite;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds a {@link Specification} for the test-suite list query from a {@link TestSuiteListFilter}.
 */
public final class TestSuiteSpecifications {

    private TestSuiteSpecifications() {
    }

    public static Specification<TestSuite> build(UUID projectId, TestSuiteListFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("project").get("id"), projectId));

            if (filter.q() != null && !filter.q().isBlank()) {
                String like = "%" + filter.q().trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("name")), like));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
