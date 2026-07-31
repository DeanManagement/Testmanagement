package com.deanmanagement.testmanagement.project.internal.repository.spec;

import com.deanmanagement.testmanagement.project.internal.dto.filter.TestCaseListFilter;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds a {@link Specification} for the test-case list query from a {@link TestCaseListFilter}.
 * Predicates use {@code LOWER(col) LIKE LOWER(...)} for Postgres/H2 portability.
 */
public final class TestCaseSpecifications {

    private TestCaseSpecifications() {
    }

    public static Specification<TestCase> build(UUID projectId, TestCaseListFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("project").get("id"), projectId));

            if (filter.folderId() != null) {
                predicates.add(cb.equal(root.get("folder").get("id"), filter.folderId()));
            } else if (filter.rootOnly()) {
                predicates.add(cb.isNull(root.get("folder")));
            }

            if (filter.q() != null && !filter.q().isBlank()) {
                String like = "%" + filter.q().trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), like),
                        cb.like(cb.lower(root.get("key")), like)
                ));
            }

            if (filter.status() != null && !filter.status().isEmpty()) {
                predicates.add(root.get("status").in(filter.status()));
            }

            if (filter.priority() != null && !filter.priority().isEmpty()) {
                predicates.add(root.get("priority").in(filter.priority()));
            }

            if (filter.label() != null && !filter.label().isEmpty()) {
                // Correlated EXISTS subquery over the @ElementCollection to avoid join-induced
                // row duplication breaking pagination/count.
                Subquery<Integer> sub = query.subquery(Integer.class);
                Root<TestCase> subRoot = sub.from(TestCase.class);
                Join<TestCase, String> labels = subRoot.join("labels");
                sub.select(cb.literal(1));
                sub.where(cb.equal(subRoot, root), labels.in(filter.label()));
                predicates.add(cb.exists(sub));
            }

            if (filter.updatedAfter() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("updatedAt"), filter.updatedAfter()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
