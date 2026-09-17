package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;

import java.util.UUID;

/**
 * Resolves a test case reference that may be either a UUID or a key (PROJ-12) — the test case
 * counterpart of {@link McpRunReferences}.
 */
final class McpTestCaseReferences {

    private McpTestCaseReferences() {
    }

    /**
     * Only ever looks inside the caller's project. A case in another project reports as not-found:
     * to this caller it may as well not exist, and saying "forbidden" would confirm it does
     * (PRD-021 discipline).
     */
    static TestCase resolve(TestCaseRepository testCases, UUID projectId, String idOrKey) {
        if (idOrKey == null || idOrKey.isBlank()) {
            throw new McpToolException("A test case id or key is required, e.g. PROJ-12.");
        }
        String reference = idOrKey.trim();
        try {
            UUID id = UUID.fromString(reference);
            return testCases.findById(id)
                    .filter(testCase -> testCase.getProject().getId().equals(projectId))
                    .orElseThrow(() -> new ResourceNotFoundException("TestCase", reference));
        } catch (IllegalArgumentException notAUuid) {
            return testCases.findByKeyAndProjectId(reference, projectId)
                    .orElseThrow(() -> new ResourceNotFoundException("TestCase", reference));
        }
    }
}
