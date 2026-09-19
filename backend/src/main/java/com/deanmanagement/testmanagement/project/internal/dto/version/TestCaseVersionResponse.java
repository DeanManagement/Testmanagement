package com.deanmanagement.testmanagement.project.internal.dto.version;

import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** One historical state of a test case (PRD-011). */
public record TestCaseVersionResponse(
        UUID id,
        int versionNumber,
        Instant versionAt,
        String title,
        String description,
        String preconditions,
        Priority priority,
        TestCaseStatus status,
        List<String> labels,
        List<StepSnapshot> steps,
        UUID createdBy,
        /* PRD-035: values by field name; empty for versions saved before custom fields existed. */
        Map<String, Object> customFields,
        /* PRD-036 */
        Integer estimateMinutes
) {
    /**
     * A step as it was executed. Since PRD-030 snapshots hold the expanded steps, with the shared
     * block each came from; older snapshots have no {@code sharedStepTitle}.
     */
    public record StepSnapshot(
            int orderIndex,
            String action,
            String expectedResult,
            String testData,
            String sharedStepTitle
    ) {
        public StepSnapshot(int orderIndex, String action, String expectedResult, String testData) {
            this(orderIndex, action, expectedResult, testData, null);
        }
    }
}
