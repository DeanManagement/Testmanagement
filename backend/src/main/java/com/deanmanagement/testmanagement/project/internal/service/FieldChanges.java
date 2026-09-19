package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.user.User;

import java.math.BigDecimal;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * What an update changed, old and new, for the audit trail (PRD-046). Take a {@link Snapshot} of the
 * fields before mutating and another after, then diff them. Values are rendered to text when the
 * snapshot is taken, so a collection mutated in place still shows its old contents.
 */
public final class FieldChanges {

    static final int MAX_VALUE_LENGTH = 200;
    private static final String ELLIPSIS = "…";
    private static final FieldChanges NONE = new FieldChanges(List.of());

    /** One changed field; {@code null} means the field was empty. */
    public record Change(String field, String from, String to) {
    }

    private final List<Change> changes;

    private FieldChanges(List<Change> changes) {
        this.changes = changes;
    }

    public static FieldChanges none() {
        return NONE;
    }

    /** A single change, for a caller that knows both values without a snapshot. */
    public static FieldChanges of(String field, Object from, Object to) {
        return between(new Snapshot().with(field, from), new Snapshot().with(field, to));
    }

    /** The fields whose rendered value differs; null and blank count as the same. */
    public static FieldChanges between(Snapshot before, Snapshot after) {
        List<Change> changes = new ArrayList<>();
        before.values.forEach((field, from) -> {
            String to = after.values.get(field);
            if (!Objects.equals(from, to)) {
                changes.add(new Change(field, from, to));
            }
        });
        return changes.isEmpty() ? NONE : new FieldChanges(List.copyOf(changes));
    }

    public FieldChanges and(FieldChanges other) {
        if (other.isEmpty()) {
            return this;
        }
        List<Change> all = new ArrayList<>(changes);
        all.addAll(other.changes);
        return new FieldChanges(List.copyOf(all));
    }

    public boolean isEmpty() {
        return changes.isEmpty();
    }

    public List<Change> list() {
        return changes;
    }

    /** Field values at one moment, already rendered to text. */
    public static final class Snapshot {
        private final Map<String, String> values = new LinkedHashMap<>();

        public Snapshot with(String field, Object value) {
            values.put(field, render(value));
            return this;
        }
    }

    static String render(Object value) {
        String text = switch (value) {
            case null -> null;
            case Enum<?> e -> e.name();
            case User user -> user.getDisplayName();
            // 80.00 from the database and 80 from a request are the same gate.
            case BigDecimal number -> number.stripTrailingZeros().toPlainString();
            case Collection<?> items -> items.stream().map(FieldChanges::render).filter(Objects::nonNull)
                    .sorted().collect(Collectors.joining(", "));
            case TemporalAccessor time -> time.toString();
            default -> value.toString();
        };
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.length() > MAX_VALUE_LENGTH ? text.substring(0, MAX_VALUE_LENGTH) + ELLIPSIS : text;
    }
}
