package com.deanmanagement.testmanagement.shared;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;

/**
 * Normalizes incoming {@link Pageable} requests for list endpoints: clamps the page size, applies a
 * deterministic default sort, and always appends an {@code id} tiebreaker so pages don't drift when
 * the primary sort key has ties.
 */
public final class PageableUtils {

    public static final int DEFAULT_SIZE = 50;
    public static final int MAX_SIZE = 200;
    private static final String DEFAULT_SORT_PROPERTY = "updatedAt";

    private PageableUtils() {
    }

    public static Pageable normalize(Pageable pageable) {
        int page = pageable.isPaged() ? Math.max(pageable.getPageNumber(), 0) : 0;

        int requestedSize = pageable.isPaged() ? pageable.getPageSize() : DEFAULT_SIZE;
        int size = requestedSize <= 0 ? DEFAULT_SIZE : Math.min(requestedSize, MAX_SIZE);

        Sort sort = pageable.getSort();
        if (sort.isUnsorted()) {
            sort = Sort.by(Sort.Order.desc(DEFAULT_SORT_PROPERTY));
        }
        sort = withIdTiebreaker(sort);

        return PageRequest.of(page, size, sort);
    }

    private static Sort withIdTiebreaker(Sort sort) {
        boolean hasId = sort.stream().anyMatch(o -> o.getProperty().equals("id"));
        if (hasId) {
            return sort;
        }
        List<Sort.Order> orders = new ArrayList<>();
        sort.forEach(orders::add);
        orders.add(Sort.Order.asc("id"));
        return Sort.by(orders);
    }
}
