package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.testSuite.CreateTestSuiteRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testSuite.TestSuiteMapper;
import com.deanmanagement.testmanagement.project.internal.dto.testSuite.TestSuiteReportResponse;
import com.deanmanagement.testmanagement.project.internal.dto.TestSuiteResponse;
import com.deanmanagement.testmanagement.project.internal.dto.UpdateTestSuiteRequest;
import com.deanmanagement.testmanagement.project.internal.dto.filter.TestSuiteListFilter;
import com.deanmanagement.testmanagement.project.internal.repository.spec.TestSuiteSpecifications;
import com.deanmanagement.testmanagement.project.internal.entity.AuditAction;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestResult;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestSuite;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestResultRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestSuiteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TestSuiteService {

    private final TestSuiteRepository testSuiteRepository;
    private final ProjectRepository projectRepository;
    private final TestCaseRepository testCaseRepository;
    private final TestResultRepository testResultRepository;
    private final TestSuiteMapper testSuiteMapper;
    private final AuditService auditService;

    public Page<TestSuiteResponse> findByProject(UUID projectId, TestSuiteListFilter filter, Pageable pageable) {
        return testSuiteRepository.findAll(TestSuiteSpecifications.build(projectId, filter), pageable)
                .map(testSuiteMapper::toResponse);
    }

    public TestSuiteResponse findById(UUID projectId, UUID id) {
        TestSuite suite = testSuiteRepository.findById(id)
                .filter(s -> s.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("TestSuite", id));
        return testSuiteMapper.toResponse(suite);
    }

    @Transactional
    public TestSuiteResponse create(UUID projectId, CreateTestSuiteRequest request, UUID userId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

        TestSuite suite = testSuiteMapper.toEntity(request);
        suite.setProject(project);
        suite.setTestCases(resolveTestCases(projectId, request.testCaseIds()));

        suite = testSuiteRepository.save(suite);
        auditService.log(projectId, userId, AuditAction.CREATED,
                AuditEntityType.TEST_SUITE, suite.getId(), suite.getName(), null);
        return testSuiteMapper.toResponse(suite);
    }

    @Transactional
    public TestSuiteResponse update(UUID projectId, UUID id, UpdateTestSuiteRequest request, UUID userId) {
        TestSuite suite = testSuiteRepository.findById(id)
                .filter(s -> s.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("TestSuite", id));

        suite.setName(request.name());
        suite.setDescription(request.description());
        if (request.testCaseIds() != null) {
            suite.setTestCases(resolveTestCases(projectId, request.testCaseIds()));
        }

        suite = testSuiteRepository.save(suite);
        auditService.log(projectId, userId, AuditAction.UPDATED,
                AuditEntityType.TEST_SUITE, suite.getId(), suite.getName(), null);
        return testSuiteMapper.toResponse(suite);
    }

    @Transactional
    public void delete(UUID projectId, UUID id, UUID userId) {
        TestSuite suite = testSuiteRepository.findById(id)
                .filter(s -> s.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("TestSuite", id));
        auditService.log(projectId, userId, AuditAction.DELETED,
                AuditEntityType.TEST_SUITE, suite.getId(), suite.getName(), null);
        testSuiteRepository.delete(suite);
    }

    public TestSuiteReportResponse getReport(UUID projectId, UUID suiteId) {
        TestSuite suite = testSuiteRepository.findById(suiteId)
                .filter(s -> s.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("TestSuite", suiteId));

        Set<TestCase> testCases = suite.getTestCases();
        int total = testCases.size();

        if (testCases.isEmpty()) {
            return new TestSuiteReportResponse(
                    suite.getId(), suite.getName(), suite.getDescription(),
                    0, 0, 0, 0, 0, 0, 0.0, List.of());
        }

        Set<UUID> testCaseIds = testCases.stream().map(TestCase::getId).collect(Collectors.toSet());
        List<TestResult> allResults = testResultRepository.findByTestCaseIdsAndCompletedRuns(testCaseIds, projectId);

        // Group by testCaseId, pick first (most recent due to ORDER BY updatedAt DESC)
        Map<UUID, TestResult> latestByTestCase = new LinkedHashMap<>();
        for (TestResult result : allResults) {
            latestByTestCase.putIfAbsent(result.getTestCase().getId(), result);
        }

        List<TestSuiteReportResponse.TestCaseLatestResult> results = new ArrayList<>();
        for (TestCase tc : testCases) {
            TestResult latest = latestByTestCase.get(tc.getId());
            if (latest != null) {
                results.add(new TestSuiteReportResponse.TestCaseLatestResult(
                        tc.getId(), tc.getTitle(),
                        latest.getStatus(),
                        latest.getTestRun().getId(), latest.getTestRun().getName(),
                        latest.getUpdatedAt()));
            } else {
                results.add(new TestSuiteReportResponse.TestCaseLatestResult(
                        tc.getId(), tc.getTitle(),
                        null, null, null, null));
            }
        }

        int passed = (int) results.stream().filter(r -> r.status() == TestResultStatus.PASSED).count();
        int failed = (int) results.stream().filter(r -> r.status() == TestResultStatus.FAILED).count();
        int blocked = (int) results.stream().filter(r -> r.status() == TestResultStatus.BLOCKED).count();
        int skipped = (int) results.stream().filter(r -> r.status() == TestResultStatus.SKIPPED).count();
        int untested = (int) results.stream().filter(r -> r.status() == null).count();
        double passRate = total > 0 ? Math.round(passed * 10000.0 / total) / 100.0 : 0.0;

        return new TestSuiteReportResponse(
                suite.getId(), suite.getName(), suite.getDescription(),
                total, passed, failed, blocked, skipped, untested, passRate, results);
    }

    @Transactional
    public void bulkAddTestCases(UUID projectId, UUID suiteId, Set<UUID> testCaseIds, UUID userId) {
        TestSuite suite = testSuiteRepository.findById(suiteId)
                .filter(s -> s.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("TestSuite", suiteId));

        Set<TestCase> toAdd = resolveTestCases(projectId, testCaseIds);
        suite.getTestCases().addAll(toAdd);
        testSuiteRepository.save(suite);

        auditService.log(projectId, userId, AuditAction.UPDATED,
                AuditEntityType.TEST_SUITE, suite.getId(), suite.getName(),
                "Added " + toAdd.size() + " test cases");
    }

    @Transactional
    public void bulkRemoveTestCases(UUID projectId, UUID suiteId, Set<UUID> testCaseIds, UUID userId) {
        TestSuite suite = testSuiteRepository.findById(suiteId)
                .filter(s -> s.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("TestSuite", suiteId));

        suite.getTestCases().removeIf(tc -> testCaseIds.contains(tc.getId()));
        testSuiteRepository.save(suite);

        auditService.log(projectId, userId, AuditAction.UPDATED,
                AuditEntityType.TEST_SUITE, suite.getId(), suite.getName(),
                "Removed " + testCaseIds.size() + " test cases");
    }

    /**
     * Resolves the supplied ids <em>within the suite's project</em>, and refuses the whole call if
     * any of them is unknown there.
     *
     * <p>Both halves matter. Without the project filter, a caller could attach another project's
     * test cases to its own suite and then read their titles back through the suite — a
     * cross-project write and an information leak in one move, which is reachable from both the
     * REST API and the MCP {@code create_test_suite} tool. And silently dropping ids that do not
     * resolve turns a typo into a quietly half-empty suite; a caller that names a case it cannot
     * see should be told, in the same not-found terms used everywhere else so that existence in
     * another project is not disclosed (PRD-021 discipline).
     */
    private Set<TestCase> resolveTestCases(UUID projectId, Set<UUID> testCaseIds) {
        if (testCaseIds == null || testCaseIds.isEmpty()) {
            return new HashSet<>();
        }
        List<TestCase> found = testCaseRepository.findByIdInAndProjectId(testCaseIds, projectId);
        if (found.size() != testCaseIds.size()) {
            Set<UUID> missing = new HashSet<>(testCaseIds);
            found.forEach(testCase -> missing.remove(testCase.getId()));
            throw new ResourceNotFoundException("TestCase", missing.iterator().next());
        }
        return new HashSet<>(found);
    }
}
