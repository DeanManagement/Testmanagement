package com.deanmanagement.testmanagement.project.internal.repository.spec;

import com.deanmanagement.testmanagement.project.internal.dto.filter.CustomFieldCriterion;
import com.deanmanagement.testmanagement.project.internal.entity.CustomFieldType;
import com.deanmanagement.testmanagement.project.internal.entity.CustomFieldValue;
import jakarta.persistence.criteria.CommonAbstractCriteria;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Custom field predicates shared by the test case and test run list queries (PRD-035 §3.5). Each
 * criterion is one correlated {@code EXISTS} over {@code custom_field_values}, the same shape as the
 * label predicate, so multi-select rows never duplicate list rows or break page counts.
 */
final class CustomFieldSpecifications {

    private CustomFieldSpecifications() {
    }

    /**
     * @param ownerAttribute the {@link CustomFieldValue} attribute pointing at {@code owner}:
     *                       {@code testCase} or {@code testRun}
     */
    static List<Predicate> matchAll(CommonAbstractCriteria query, CriteriaBuilder cb, Root<?> owner,
                                    String ownerAttribute, List<CustomFieldCriterion> criteria) {
        List<Predicate> predicates = new ArrayList<>();
        if (criteria != null) {
            for (CustomFieldCriterion criterion : criteria) {
                predicates.add(matches(query, cb, owner, ownerAttribute, criterion));
            }
        }
        return predicates;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Predicate matches(CommonAbstractCriteria query, CriteriaBuilder cb, Root<?> owner,
                                     String ownerAttribute, CustomFieldCriterion criterion) {
        Subquery<Integer> sub = query.subquery(Integer.class);
        Root<CustomFieldValue> value = sub.from(CustomFieldValue.class);
        Path<Comparable> column = value.get(criterion.valueAttribute());

        List<Predicate> where = new ArrayList<>();
        where.add(cb.equal(value.get(ownerAttribute), owner));
        where.add(cb.equal(value.get("field").get("id"), criterion.fieldId()));
        if (!criterion.values().isEmpty()) {
            where.add(criterion.fieldType() == CustomFieldType.TEXT
                    ? containsAny(cb, value.get("valueText"), criterion.values())
                    : column.in(criterion.values()));
        }
        if (criterion.min() != null) {
            where.add(cb.greaterThanOrEqualTo(column, (Comparable) criterion.min()));
        }
        if (criterion.max() != null) {
            where.add(cb.lessThanOrEqualTo(column, (Comparable) criterion.max()));
        }
        sub.select(cb.literal(1)).where(where.toArray(new Predicate[0]));
        return cb.exists(sub);
    }

    private static Predicate containsAny(CriteriaBuilder cb, Expression<String> text, List<?> needles) {
        Expression<String> lowered = cb.lower(text);
        return cb.or(needles.stream()
                .map(needle -> cb.like(lowered, "%" + needle.toString().toLowerCase(Locale.ROOT) + "%"))
                .toArray(Predicate[]::new));
    }
}
