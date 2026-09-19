package com.deanmanagement.testmanagement.project.internal.repository.spec;

import com.deanmanagement.testmanagement.project.internal.dto.bugReport.BugReportFilter;
import com.deanmanagement.testmanagement.project.internal.entity.BugReport;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The bug list query (PRD-045 §3.2). Search is a case-insensitive LIKE over the key and every text
 * field: bug volume per project is in the hundreds, so no full-text index is needed.
 */
public final class BugReportSpecifications {

    private static final List<String> SEARCHED_FIELDS = List.of("key", "title", "description",
            "stepsToReproduce", "expectedBehavior", "actualBehavior", "environment");

    private BugReportSpecifications() {
    }

    public static Specification<BugReport> build(UUID projectId, BugReportFilter filter) {
        return (root, query, cb) -> {
            // Fetch what the response reads, on the row query only: a count query cannot carry fetches.
            if (BugReport.class.equals(query.getResultType())) {
                root.fetch("assignee", JoinType.LEFT);
                root.fetch("testResult", JoinType.LEFT).fetch("testCase", JoinType.LEFT);
                root.fetch("testRun", JoinType.LEFT);
                root.fetch("duplicateOf", JoinType.LEFT);
            }

            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("project").get("id"), projectId));

            if (filter.q() != null && !filter.q().isBlank()) {
                predicates.add(matchesText(root, cb, filter.q()));
            }
            if (filter.status() != null && !filter.status().isEmpty()) {
                predicates.add(root.get("status").in(filter.status()));
            }
            if (filter.priority() != null && !filter.priority().isEmpty()) {
                predicates.add(root.get("priority").in(filter.priority()));
            }
            if (filter.filtersAssignee()) {
                predicates.add(matchesAssignee(root, cb, filter));
            }
            if (filter.testResultId() != null) {
                predicates.add(cb.equal(root.get("testResult").get("id"), filter.testResultId()));
            }
            if (filter.environmentId() != null) {
                predicates.add(cb.equal(root.get("projectEnvironment").get("id"), filter.environmentId()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static Predicate matchesText(Root<BugReport> root, CriteriaBuilder cb, String q) {
        String like = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        List<Predicate> any = new ArrayList<>();
        for (String field : SEARCHED_FIELDS) {
            Expression<String> value = root.get(field);
            any.add(cb.like(cb.lower(value), like));
        }
        return cb.or(any.toArray(new Predicate[0]));
    }

    private static Predicate matchesAssignee(Root<BugReport> root, CriteriaBuilder cb, BugReportFilter filter) {
        List<Predicate> any = new ArrayList<>();
        if (filter.assigneeIds() != null && !filter.assigneeIds().isEmpty()) {
            // An explicit left join: an implicit one would be inner and drop the unassigned rows the
            // other branch of this OR is there to keep.
            any.add(root.join("assignee", JoinType.LEFT).get("id").in(filter.assigneeIds()));
        }
        if (filter.includeUnassigned()) {
            any.add(cb.isNull(root.get("assignee")));
        }
        return cb.or(any.toArray(new Predicate[0]));
    }
}
