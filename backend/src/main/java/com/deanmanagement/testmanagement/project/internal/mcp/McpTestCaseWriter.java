package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.dto.TestStepRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.CreateTestCaseRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.TestCaseResponse;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.service.TestCaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Creates one test case in its own transaction (PRD-025 §4).
 *
 * <p>A separate bean because {@code REQUIRES_NEW} only takes effect through the Spring proxy, and
 * this is the whole point of it. {@code create_test_cases_bulk} promises the agent that items
 * succeed or fail independently. Without a fresh transaction per item, a failure on item 37 marks
 * the shared transaction rollback-only, the loop keeps going, the tool cheerfully reports 36
 * created — and then the commit throws {@code UnexpectedRollbackException} and all 36 vanish.
 * The agent would have been told the opposite of what happened.
 */
@Service
@RequiredArgsConstructor
public class McpTestCaseWriter {

    private final TestCaseService testCaseService;
    private final McpValidator validator;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public McpDtos.CreatedTestCase create(UUID projectId, UUID userId, String title,
                                          Priority priority, String description,
                                          String preconditions, TestCaseStatus status,
                                          Set<String> labels, List<McpDtos.Step> steps,
                                          UUID folderId) {
        var request = new CreateTestCaseRequest(
                title, description, preconditions, priority,
                status == null ? TestCaseStatus.DRAFT : status,
                labels, toStepRequests(steps), folderId);
        validator.validate(request);

        TestCaseResponse response = testCaseService.create(projectId, request, userId);
        return new McpDtos.CreatedTestCase(response.id(), response.key(), response.title(),
                response.status());
    }

    static List<TestStepRequest> toStepRequests(List<McpDtos.Step> steps) {
        return steps == null ? null : steps.stream()
                .map(s -> new TestStepRequest(s.action(), s.expectedResult(), s.testData()))
                .toList();
    }
}
