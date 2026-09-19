package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.testCase.TestCaseContextResponse;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.TestCaseExecutionResponse;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseFolder;
import com.deanmanagement.testmanagement.project.internal.entity.TestResult;
import com.deanmanagement.testmanagement.project.internal.entity.TestSuite;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestResultRepository;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import com.deanmanagement.testmanagement.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * What the case page shows around a test case (PRD-050): who made it, where it sits, which suites
 * include it, and everywhere it ran. Read-only, and scoped: a case of another project is a 404.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TestCaseContextService {

    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 100;

    private final TestCaseRepository testCaseRepository;
    private final TestResultRepository testResultRepository;
    private final UserService userService;

    public TestCaseContextResponse context(UUID projectId, UUID testCaseId) {
        TestCase testCase = requireCase(projectId, testCaseId);
        Map<UUID, String> names = namesOf(Stream.of(testCase.getCreatedBy(), testCase.getUpdatedBy()));
        List<TestCaseContextResponse.Ref> suites = testCase.getTestSuites().stream()
                .sorted(Comparator.comparing(TestSuite::getName, String.CASE_INSENSITIVE_ORDER))
                .map(suite -> new TestCaseContextResponse.Ref(suite.getId(), suite.getName()))
                .toList();
        return new TestCaseContextResponse(nameOf(names, testCase.getCreatedBy()), nameOf(names, testCase.getUpdatedBy()),
                folderPath(testCase.getFolder()), suites);
    }

    /** Newest run first, pending included: where the case ran, or is scheduled to run. */
    public Page<TestCaseExecutionResponse> executions(UUID projectId, UUID testCaseId, int page, int size) {
        requireCase(projectId, testCaseId);
        int pageSize = size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        Page<TestResult> results = testResultRepository.findHistory(projectId, testCaseId,
                PageRequest.of(Math.max(page, 0), pageSize));
        Map<UUID, String> names = namesOf(results.getContent().stream().map(TestResult::getExecutedBy));
        return results.map(result -> new TestCaseExecutionResponse(result.getId(), result.getTestRun().getId(),
                result.getTestRun().getKey(), result.getTestRun().getName(), result.getTestRun().getStatus(),
                result.getTestRun().getEnvironment(), result.getParameterSetName(), result.getStatus(),
                result.getExecutedAt(), nameOf(names, result.getExecutedBy()), result.getExecutedVersion(),
                result.getDurationMs()));
    }

    private TestCase requireCase(UUID projectId, UUID testCaseId) {
        return testCaseRepository.findByIdAndProjectId(testCaseId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("TestCase", testCaseId));
    }

    /** Root first. Folders nest a few levels deep, so walking the parents is cheap. */
    private static List<TestCaseContextResponse.Ref> folderPath(TestCaseFolder folder) {
        List<TestCaseContextResponse.Ref> path = new ArrayList<>();
        for (TestCaseFolder current = folder; current != null; current = current.getParent()) {
            path.add(new TestCaseContextResponse.Ref(current.getId(), current.getName()));
        }
        Collections.reverse(path);
        return path;
    }

    private Map<UUID, String> namesOf(Stream<UUID> userIds) {
        Set<UUID> ids = userIds.filter(Objects::nonNull).collect(Collectors.toSet());
        return ids.isEmpty() ? Map.of() : userService.findDisplayNamesByIds(ids);
    }

    private static String nameOf(Map<UUID, String> names, UUID userId) {
        return userId == null ? null : names.get(userId);
    }
}
