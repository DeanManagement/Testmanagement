package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.user.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** PRD-046: what counts as a change, and how values read in the history. */
class FieldChangesTest {

    @Test
    void recordsOnlyTheFieldsThatDiffer() {
        var before = new FieldChanges.Snapshot().with("title", "Login").with("priority", Priority.LOW);
        var after = new FieldChanges.Snapshot().with("title", "Login").with("priority", Priority.HIGH);

        assertThat(FieldChanges.between(before, after).list())
                .containsExactly(new FieldChanges.Change("priority", "LOW", "HIGH"));
    }

    @Test
    void treatsNullAndBlankAsTheSame() {
        assertThat(FieldChanges.of("description", null, "  ").isEmpty()).isTrue();
        assertThat(FieldChanges.of("description", "", null).isEmpty()).isTrue();
    }

    @Test
    void cutsLongValuesButStillReportsTheChange() {
        String longText = "x".repeat(300);

        FieldChanges.Change change = FieldChanges.of("description", "short", longText).list().getFirst();

        assertThat(change.to()).hasSize(FieldChanges.MAX_VALUE_LENGTH + 1).endsWith("…");
    }

    @Test
    void rendersUsersByNameDatesAsIsoAndNumbersWithoutTrailingZeros() {
        User ada = new User();
        ada.setDisplayName("Ada");

        assertThat(FieldChanges.render(ada)).isEqualTo("Ada");
        assertThat(FieldChanges.render(LocalDate.of(2026, 9, 19))).isEqualTo("2026-09-19");
        assertThat(FieldChanges.of("gate", new BigDecimal("80.00"), new BigDecimal("80")).isEmpty()).isTrue();
    }

    @Test
    void takesCollectionsAtSnapshotTimeInAStableOrder() {
        List<String> labels = new ArrayList<>(List.of("ui", "api"));
        var before = new FieldChanges.Snapshot().with("labels", labels);
        labels.add("smoke");

        FieldChanges changes = FieldChanges.between(before, new FieldChanges.Snapshot().with("labels", labels));

        assertThat(changes.list()).containsExactly(new FieldChanges.Change("labels", "api, ui", "api, smoke, ui"));
    }
}
