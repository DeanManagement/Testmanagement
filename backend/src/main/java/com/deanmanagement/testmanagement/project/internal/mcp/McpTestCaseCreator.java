package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The guards every MCP test-case create goes through, single or bulk, before
 * {@link McpTestCaseWriter} writes it.
 *
 * <p>Its own bean rather than a method on the writer: the writer's {@code REQUIRES_NEW} only takes
 * effect through the Spring proxy, and a guard method calling {@code create} on the same instance
 * would bypass it.
 */
@Component
@RequiredArgsConstructor
class McpTestCaseCreator {

    private final McpTestCaseWriter writer;
    private final McpProperties properties;

    /** @param duplicateIndex null to skip the duplicate guard entirely */
    McpDtos.CreatedTestCase create(McpCallerContext.Caller caller, String title,
                                           Priority priority, String description,
                                           String preconditions, TestCaseStatus status,
                                           Set<String> labels, List<McpDtos.Step> steps,
                                           UUID folderId, Map<String, Object> customFields,
                                           Integer estimateMinutes,
                                           TestCaseDuplicateDetector.Index duplicateIndex) {
        if (priority == null) {
            throw new McpToolException("priority is required: LOW, MEDIUM, HIGH or CRITICAL.");
        }
        if (steps != null && steps.size() > properties.getMaxStepsPerCase()) {
            throw new McpToolException("At most " + properties.getMaxStepsPerCase()
                    + " steps per case; a case needing more is really several cases.");
        }
        if (duplicateIndex != null && title != null) {
            duplicateIndex.find(title).ifPresent(existing -> {
                throw new McpToolException(
                        "A test case with this title already exists: " + existing.key() + " — \""
                                + existing.title() + "\". Call update_test_case on it, or retry "
                                + "with allowDuplicateTitle: true if this really is a separate case.");
            });
        }
        // Its own transaction, so one bad item in a bulk call cannot take the others with it.
        return writer.create(caller.projectId(), caller.userId(), title, priority, description,
                preconditions, status, labels, steps, folderId, customFields, estimateMinutes);
    }
}
