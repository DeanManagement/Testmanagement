package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.shared.PageableUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Map;

/**
 * The columns the bug list sorts by, mapped to entity paths. An unknown column is a 400 rather than
 * the 500 an unresolvable property path would be. {@code key} sorts by creation: keys are numbered
 * in creation order, and as strings BUG-10 would sort before BUG-2.
 */
final class BugReportSort {

    private static final Map<String, String> SORTABLE = Map.of(
            "key", "createdAt",
            "title", "title",
            "priority", "priority",
            "status", "status",
            "assignee", "assignee.displayName",
            "createdAt", "createdAt",
            "updatedAt", "updatedAt",
            "id", "id");

    private BugReportSort() {
    }

    static Pageable toEntitySort(Pageable pageable) {
        Pageable normalized = PageableUtils.normalize(pageable);
        Sort sort = Sort.by(normalized.getSort().stream()
                .map(order -> order.withProperty(entityPath(order.getProperty())))
                .toList());
        return PageRequest.of(normalized.getPageNumber(), normalized.getPageSize(), sort);
    }

    private static String entityPath(String column) {
        String path = SORTABLE.get(column);
        if (path == null) {
            throw new IllegalArgumentException("Cannot sort bug reports by '" + column + "'. Sortable: "
                    + SORTABLE.keySet());
        }
        return path;
    }
}
