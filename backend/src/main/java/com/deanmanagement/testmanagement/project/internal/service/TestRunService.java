package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.CompletionInfoResponse;
import com.deanmanagement.testmanagement.project.internal.dto.customField.CustomFieldValueMaps;
import com.deanmanagement.testmanagement.project.internal.dto.effort.EffortSummary;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.CloneTestRunRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.CreateTestResultRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.CreateTestRunRequest;
import com.deanmanagement.testmanagement.project.internal.dto.StepResultResponse;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.RunAllureReportId;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.RunStatusCount;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.TestRunMapper;
import com.deanmanagement.testmanagement.project.internal.dto.TestRunResponse;
import com.deanmanagement.testmanagement.project.internal.dto.TestRunSummaryResponse;
import com.deanmanagement.testmanagement.project.internal.dto.TestResultResponse;
import com.deanmanagement.testmanagement.project.internal.dto.UpdateStepResultRequest;
import com.deanmanagement.testmanagement.project.internal.dto.UpdateTestResultRequest;
import com.deanmanagement.testmanagement.project.internal.dto.UpdateTestRunRequest;
import com.deanmanagement.testmanagement.project.internal.dto.filter.TestRunListFilter;
import com.deanmanagement.testmanagement.project.internal.repository.spec.TestRunSpecifications;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectEnvironment;
import com.deanmanagement.testmanagement.project.internal.entity.StepResult;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseParameterSet;
import com.deanmanagement.testmanagement.project.internal.entity.TestResult;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.project.internal.entity.AuditAction;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.TestPlan;
import com.deanmanagement.testmanagement.project.internal.entity.TestRun;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestStep;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import com.deanmanagement.testmanagement.project.internal.repository.AllureReportRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectMemberRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.StepResultRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestPlanRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestResultRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestRunRepository;
import com.deanmanagement.testmanagement.project.internal.entity.WebhookEventType;
import com.deanmanagement.testmanagement.project.internal.webhook.WebhookEvent;
import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.deanmanagement.testmanagement.project.internal.dto.report.TestRunReportResponse;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TestRunService {

    private final TestRunRepository testRunRepository;
    private final TestResultRepository testResultRepository;
    private final StepResultRepository stepResultRepository;
    private final ProjectRepository projectRepository;
    private final AllureReportRepository allureReportRepository;
    private final TestCaseRepository testCaseRepository;
    private final TestPlanRepository testPlanRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final TestRunMapper testRunMapper;
    private final UserService userService;
    private final AuditService auditService;
    private final ParameterSetService parameterSetService;
    private final ApplicationEventPublisher eventPublisher;
    private final ProjectSequenceService projectSequenceService;
    private final RunEventPublisher runEventPublisher;
    private final ProjectEnvironmentService environmentService;
    private final TestCaseReviewService reviewService;
    private final CustomFieldValueWriter customFieldWriter;

    private static final int MAX_RUN_NAME_LENGTH = 255;

    private static final List<TestResultStatus> SEVERITY_ORDER = List.of(
            TestResultStatus.FAILED, TestResultStatus.BLOCKED, TestResultStatus.SKIPPED,
            TestResultStatus.PENDING, TestResultStatus.PASSED
    );

    public Page<TestRunSummaryResponse> findByProject(UUID projectId, TestRunListFilter filter, Pageable pageable) {
        Page<TestRun> page = testRunRepository.findAll(TestRunSpecifications.build(projectId, filter), pageable);
        return new PageImpl<>(toSummaries(page.getContent()), pageable, page.getTotalElements());
    }

    public List<TestRunSummaryResponse> findByExecutor(UUID executorId) {
        return toSummaries(testRunRepository.findByExecutorIdWithProject(executorId));
    }

    public List<TestRunSummaryResponse> findByExecutorWithStatuses(UUID executorId, List<TestRunStatus> statuses) {
        return toSummaries(testRunRepository.findByExecutorIdAndStatusInWithProject(executorId, statuses));
    }

    /**
     * List projection: two batch queries (status counts, allure-report ids) for the whole
     * page instead of materializing every result row per run.
     */
    private List<TestRunSummaryResponse> toSummaries(List<TestRun> runs) {
        if (runs.isEmpty()) {
            return List.of();
        }
        List<UUID> runIds = runs.stream().map(TestRun::getId).toList();
        Map<UUID, Map<TestResultStatus, Long>> countsByRun = testResultRepository.countStatusByRunIds(runIds).stream()
                .collect(Collectors.groupingBy(RunStatusCount::runId,
                        Collectors.toMap(RunStatusCount::status, RunStatusCount::count)));
        Map<UUID, UUID> allureReportIds = allureReportRepository.findIdsByTestRunIds(runIds).stream()
                .collect(Collectors.toMap(RunAllureReportId::runId, RunAllureReportId::reportId));
        return runs.stream()
                .map(run -> toSummary(run,
                        countsByRun.getOrDefault(run.getId(), Map.of()),
                        allureReportIds.get(run.getId())))
                .toList();
    }

    private TestRunSummaryResponse toSummary(TestRun run, Map<TestResultStatus, Long> counts, UUID allureReportId) {
        int total = (int) counts.values().stream().mapToLong(Long::longValue).sum();
        return new TestRunSummaryResponse(
                run.getId(),
                run.getKey(),
                run.getName(),
                run.getEnvironment(),
                run.getStatus(),
                run.getStartTime(),
                run.getEndTime(),
                run.getExecutor() != null ? run.getExecutor().getDisplayName() : null,
                run.getCompletedBy() != null ? run.getCompletedBy().getDisplayName() : null,
                run.getReopenReason(),
                run.getTestPlan() != null ? run.getTestPlan().getId() : null,
                run.getTestPlan() != null ? run.getTestPlan().getName() : null,
                allureReportId,
                run.getProject().getId(),
                run.getProject().getKey(),
                total,
                counts.getOrDefault(TestResultStatus.PASSED, 0L).intValue(),
                counts.getOrDefault(TestResultStatus.FAILED, 0L).intValue(),
                counts.getOrDefault(TestResultStatus.BLOCKED, 0L).intValue(),
                counts.getOrDefault(TestResultStatus.SKIPPED, 0L).intValue(),
                counts.getOrDefault(TestResultStatus.PENDING, 0L).intValue(),
                run.getCreatedAt(),
                run.getUpdatedAt(),
                run.getCreatedBy(),
                run.getUpdatedBy()
        );
    }

    public TestRunResponse findById(UUID projectId, UUID id) {
        TestRun run = testRunRepository.findById(id)
                .filter(r -> r.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("TestRun", id));
        return testRunMapper.toResponse(run);
    }

    public TestRunReportResponse getReport(UUID projectId, UUID id) {
        TestRun run = testRunRepository.findById(id)
                .filter(r -> r.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("TestRun", id));

        List<TestResult> results = run.getResults();
        int total = results.size();
        int passed = (int) results.stream().filter(r -> r.getStatus() == TestResultStatus.PASSED).count();
        int failed = (int) results.stream().filter(r -> r.getStatus() == TestResultStatus.FAILED).count();
        int blocked = (int) results.stream().filter(r -> r.getStatus() == TestResultStatus.BLOCKED).count();
        int skipped = (int) results.stream().filter(r -> r.getStatus() == TestResultStatus.SKIPPED).count();
        int pending = (int) results.stream().filter(r -> r.getStatus() == TestResultStatus.PENDING).count();
        double passRate = total > 0 ? Math.round(passed * 10000.0 / total) / 100.0 : 0.0;

        List<TestResultResponse> resultResponses = results.stream()
                .map(testRunMapper::toResultResponse)
                .toList();

        return new TestRunReportResponse(
                run.getId(), run.getName(), run.getEnvironment(), run.getStatus(),
                run.getStartTime(), run.getEndTime(),
                total, passed, failed, blocked, skipped, pending, passRate,
                resultResponses, reviewService.resultsOnUnapprovedWording(run.getProject(), results),
                EffortSummary.of(results)
        );
    }

    public CompletionInfoResponse getCompletionInfo(UUID projectId, UUID id) {
        TestRun run = testRunRepository.findById(id)
                .filter(r -> r.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("TestRun", id));

        List<TestResult> results = run.getResults();
        int total = results.size();
        int passed = (int) results.stream().filter(r -> r.getStatus() == TestResultStatus.PASSED).count();
        int failed = (int) results.stream().filter(r -> r.getStatus() == TestResultStatus.FAILED).count();
        int blocked = (int) results.stream().filter(r -> r.getStatus() == TestResultStatus.BLOCKED).count();
        int skipped = (int) results.stream().filter(r -> r.getStatus() == TestResultStatus.SKIPPED).count();
        int pending = (int) results.stream().filter(r -> r.getStatus() == TestResultStatus.PENDING).count();

        String worstStatus = computeWorstStatus(results);

        return new CompletionInfoResponse(total, passed, failed, blocked, skipped, pending, worstStatus);
    }

    @Transactional
    public TestRunResponse create(UUID projectId, CreateTestRunRequest request, UUID userId) {
        return create(projectId, request, userId, CustomFieldWriteMode.INTERACTIVE);
    }

    /** {@code mode} decides whether required custom fields must be filled (PRD-035 §3.3). */
    @Transactional
    public TestRunResponse create(UUID projectId, CreateTestRunRequest request, UUID userId,
                                  CustomFieldWriteMode mode) {
        if (request.environmentIds() != null && !request.environmentIds().isEmpty()) {
            throw new IllegalArgumentException("environmentIds creates several runs; use /test-runs/across-environments");
        }
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

        TestRun run = testRunMapper.toEntity(request);
        run.setProject(project);
        run.assignEnvironment(environmentService.resolve(projectId, request.environmentId(), request.environment()));
        run.setStatus(TestRunStatus.PLANNED);
        int runNumber = projectSequenceService.nextTestRunNumber(projectId);
        run.setKey(project.getKey() + "-Run-" + runNumber);
        customFieldWriter.write(run, request.customFields(), mode);

        if (request.testPlanId() != null) {
            TestPlan testPlan = testPlanRepository.findByIdAndProjectId(request.testPlanId(), projectId)
                    .orElseThrow(() -> new ResourceNotFoundException("TestPlan", request.testPlanId()));
            run.setTestPlan(testPlan);
        }

        if (request.executorId() != null) {
            run.setExecutor(requireProjectMember(projectId, request.executorId()));
        }

        // If test case IDs provided, create pending results for each
        if (request.testCaseIds() != null && !request.testCaseIds().isEmpty()) {
            List<TestCase> testCases = resolveTestCases(projectId, request.testCaseIds());
            for (TestCase tc : testCases) {
                // A parameterized case expands into one result per set (PRD-015); a case with no
                // sets yields exactly one result, byte-for-byte as before this feature existed.
                List<TestCaseParameterSet> parameterSets = parameterSetService.setsFor(tc.getId());
                if (parameterSets.isEmpty()) {
                    run.getResults().add(newPendingResult(run, tc, null));
                } else {
                    for (TestCaseParameterSet set : parameterSets) {
                        run.getResults().add(newPendingResult(run, tc, set));
                    }
                }
            }
        }

        run = testRunRepository.save(run);
        auditService.log(projectId, userId, AuditAction.CREATED,
                AuditEntityType.TEST_RUN, run.getId(), run.getName(), null);
        return testRunMapper.toResponse(run);
    }

    /**
     * One run per environment, same cases, plan and executor (PRD-032 §3.2), each named
     * {@code "<name> · <environment>"} with its own key. All or nothing: every id is checked
     * before the first run is written, and it is one transaction.
     */
    @Transactional
    public List<TestRunResponse> createAcrossEnvironments(UUID projectId, CreateTestRunRequest request, UUID userId) {
        List<UUID> environmentIds = request.environmentIds() == null ? List.of()
                : request.environmentIds().stream().distinct().toList();
        if (environmentIds.isEmpty()) {
            throw new IllegalArgumentException("environmentIds must name at least one environment");
        }
        if (environmentIds.size() > CreateTestRunRequest.MAX_ENVIRONMENTS) {
            throw new IllegalArgumentException("At most " + CreateTestRunRequest.MAX_ENVIRONMENTS + " environments");
        }
        if (request.environment() != null || request.environmentId() != null) {
            throw new IllegalArgumentException("Pass environmentIds, or environment/environmentId, not both");
        }
        List<ProjectEnvironment> environments = environmentIds.stream()
                .map(id -> environmentService.resolve(projectId, id, null))
                .toList();
        return environments.stream()
                .map(environment -> create(projectId, new CreateTestRunRequest(
                        runNameFor(request.name(), environment), null, request.testCaseIds(),
                        request.testPlanId(), request.executorId(), environment.getId(), null,
                        request.customFields()), userId))
                .toList();
    }

    private static String runNameFor(String name, ProjectEnvironment environment) {
        String combined = name + " · " + environment.getName();
        return combined.length() > MAX_RUN_NAME_LENGTH ? combined.substring(0, MAX_RUN_NAME_LENGTH) : combined;
    }

    /**
     * One pending result with its step results. {@code set} is null for an ordinary case; when
     * present, the set's name and values are copied onto the result so the execution stays
     * reproducible after the template or the set is edited.
     */
    private TestResult newPendingResult(TestRun run, TestCase tc, TestCaseParameterSet set) {
        TestResult result = new TestResult();
        result.setTestRun(run);
        result.setTestCase(tc);
        // Stamp the version being executed (PRD-011). Later edits to the case bump its
        // current version but must not rewrite what this result ran against.
        result.setExecutedVersion(tc.getCurrentVersion());
        result.setStatus(TestResultStatus.PENDING);
        if (set != null) {
            result.setParameterSetName(set.getName());
            result.setParameterValuesJson(set.getValuesJson());
        }

        // Shared blocks expanded (PRD-030): results point at the block's own steps, in order.
        List<TestStep> steps = StepExpansion.expandedSteps(tc.getSteps());
        for (int i = 0; i < steps.size(); i++) {
            StepResult stepResult = new StepResult();
            stepResult.setTestResult(result);
            stepResult.setTestStep(steps.get(i));
            stepResult.setPosition(i);
            stepResult.setStatus(TestResultStatus.PENDING);
            result.getStepResults().add(stepResult);
        }
        return result;
    }

    @Transactional
    public TestRunResponse cloneRun(UUID projectId, UUID runId, CloneTestRunRequest request, UUID userId) {
        TestRun source = testRunRepository.findById(runId)
                .filter(r -> r.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("TestRun", runId));

        Set<UUID> testCaseIds = source.getResults().stream()
                .map(r -> r.getTestCase().getId())
                .collect(Collectors.toSet());

        // The clone keeps the source's environment unless the request names one ("" clears it).
        boolean keepsEnvironment = request.environmentId() == null && request.environment() == null;
        CreateTestRunRequest createRequest = new CreateTestRunRequest(
                request.name(),
                request.environment(),
                testCaseIds,
                null,
                null,
                keepsEnvironment && source.getProjectEnvironment() != null
                        ? source.getProjectEnvironment().getId() : request.environmentId(),
                null,
                CustomFieldValueMaps.toMap(source.getCustomFieldValues())
        );
        // MACHINE: a clone copies what the source had, even if a field became required since.
        TestRunResponse response = create(projectId, createRequest, userId, CustomFieldWriteMode.MACHINE);
        auditService.log(projectId, userId, AuditAction.CLONED,
                AuditEntityType.TEST_RUN, response.id(), response.name(),
                "Cloned from: " + source.getName());
        return response;
    }

    @Transactional
    public TestRunResponse update(UUID projectId, UUID id, UpdateTestRunRequest request, UUID currentUserId) {
        TestRun run = testRunRepository.findById(id)
                .filter(r -> r.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("TestRun", id));

        // Null-guarded so a caller that only means to change the status does not write back the
        // name and environment it read a moment earlier, silently reverting a human's concurrent
        // edit. Same fix, and the same reason, as PRD-025 §8 applied to TestCaseService.update:
        // null now means "unchanged", and blank is refused rather than stored (PRD-027 §3.2).
        if (request.name() != null) {
            if (request.name().isBlank()) {
                throw new IllegalArgumentException("Test run name must not be blank");
            }
            run.setName(request.name());
        }
        // Null (both) means unchanged; "" clears; an id wins over a name (PRD-032 §3.2).
        if (request.environmentId() != null || request.environment() != null) {
            run.assignEnvironment(environmentService.resolve(projectId, request.environmentId(), request.environment()));
        }

        if (request.testPlanId() != null) {
            // Scoped for the same reason as create(): moving an existing run into another
            // project's plan is the same cross-project write, just through PUT (PRD-027 §3.5).
            TestPlan testPlan = testPlanRepository.findByIdAndProjectId(request.testPlanId(), projectId)
                    .orElseThrow(() -> new ResourceNotFoundException("TestPlan", request.testPlanId()));
            run.setTestPlan(testPlan);
        }
        customFieldWriter.write(run, request.customFields(), CustomFieldWriteMode.INTERACTIVE);

        TestRunStatus oldStatus = run.getStatus();
        if (request.status() != null) {
            run.setStatus(request.status());

            if (request.status() == TestRunStatus.IN_PROGRESS && oldStatus == TestRunStatus.PLANNED) {
                run.setStartTime(Instant.now());
            } else if (request.status() == TestRunStatus.IN_PROGRESS && oldStatus == TestRunStatus.COMPLETED) {
                // Reopen
                if (request.reopenReason() == null || request.reopenReason().isBlank()) {
                    throw new IllegalArgumentException("Reopen reason is required when reopening a completed test run");
                }
                run.setReopenReason(request.reopenReason());
                run.setEndTime(null);
                if (currentUserId != null) {
                    User user = userService.findEntityById(currentUserId).orElse(null);
                    run.setCompletedBy(user);
                }
            } else if (request.status() == TestRunStatus.COMPLETED) {
                run.setEndTime(Instant.now());
                run.setReopenReason(null);
                if (currentUserId != null) {
                    User user = userService.findEntityById(currentUserId).orElse(null);
                    run.setCompletedBy(user);
                }
            } else if (request.status() == TestRunStatus.ABORTED) {
                run.setEndTime(Instant.now());
            }
        }

        AuditAction auditAction = AuditAction.UPDATED;
        if (request.status() != null) {
            if (request.status() == TestRunStatus.COMPLETED) {
                auditAction = AuditAction.COMPLETED;
            } else if (request.status() == TestRunStatus.IN_PROGRESS && oldStatus == TestRunStatus.COMPLETED) {
                auditAction = AuditAction.REOPENED;
            } else {
                auditAction = AuditAction.STATUS_CHANGED;
            }
        }

        run = testRunRepository.save(run);
        auditService.log(projectId, currentUserId, auditAction,
                AuditEntityType.TEST_RUN, run.getId(), run.getName(), null);

        if (request.status() != null && request.status() != oldStatus) {
            if (request.status() == TestRunStatus.IN_PROGRESS && oldStatus == TestRunStatus.PLANNED) {
                runEventPublisher.publishStarted(run);
            } else if (request.status() == TestRunStatus.COMPLETED) {
                runEventPublisher.publishFinished(run);
            }
        }
        return testRunMapper.toResponse(run);
    }

    private void publishTestFailedEvent(TestRun run, TestResult result) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("runId", run.getId().toString());
        data.put("runKey", run.getKey());
        data.put("testCaseId", result.getTestCase() != null ? result.getTestCase().getId().toString() : null);
        data.put("testCaseKey", result.getTestCase() != null ? result.getTestCase().getKey() : null);
        data.put("testCaseTitle", result.getTestCase() != null ? result.getTestCase().getTitle() : null);
        eventPublisher.publishEvent(new WebhookEvent(WebhookEventType.TEST_FAILED, run.getProject().getId(), data));
    }

    @Transactional
    public void delete(UUID projectId, UUID id, UUID userId) {
        TestRun run = testRunRepository.findById(id)
                .filter(r -> r.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("TestRun", id));
        auditService.log(projectId, userId, AuditAction.DELETED,
                AuditEntityType.TEST_RUN, run.getId(), run.getName(), null);
        testRunRepository.delete(run);
    }

    @Transactional
    public TestResultResponse addResult(UUID projectId, UUID runId, CreateTestResultRequest request) {
        TestRun run = testRunRepository.findById(runId)
                .filter(r -> r.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("TestRun", runId));

        // Scoped: an ad-hoc result naming another project's case would put that case's title into
        // this run's report, readable by anyone who can see the run (PRD-027 §3.5).
        TestCase testCase = testCaseRepository.findByIdAndProjectId(request.testCaseId(), projectId)
                .orElseThrow(() -> new ResourceNotFoundException("TestCase", request.testCaseId()));

        TestResult result = new TestResult();
        result.setTestRun(run);
        result.setTestCase(testCase);
        result.setExecutedVersion(testCase.getCurrentVersion());
        result.setStatus(request.status());
        result.setComment(request.comment());
        result.setDefectLink(request.defectLink());
        result.setDurationMs(request.durationMs());

        result = testResultRepository.save(result);
        if (request.status() == TestResultStatus.FAILED) {
            publishTestFailedEvent(run, result);
        }
        return testRunMapper.toResultResponse(result);
    }

    @Transactional
    public TestResultResponse updateResult(UUID projectId, UUID runId, UUID resultId,
                                           UpdateTestResultRequest request) {
        TestRun run = testRunRepository.findById(runId)
                .filter(r -> r.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("TestRun", runId));

        TestResult result = testResultRepository.findById(resultId)
                .filter(r -> r.getTestRun().getId().equals(run.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("TestResult", resultId));

        result.setStatus(request.status());
        result.setComment(request.comment());
        result.setDefectLink(request.defectLink());
        // Null leaves it alone; it also survives a return to PENDING, because the effort was real.
        if (request.durationMs() != null) {
            result.setDurationMs(request.durationMs());
        }

        result = testResultRepository.save(result);
        if (request.status() == TestResultStatus.FAILED) {
            publishTestFailedEvent(run, result);
        }
        return testRunMapper.toResultResponse(result);
    }

    @Transactional
    public com.deanmanagement.testmanagement.project.internal.dto.testCase.BulkOperationResponse bulkUpdateResultStatus(
            UUID projectId, UUID runId,
            com.deanmanagement.testmanagement.project.internal.dto.testrun.BulkResultStatusRequest request,
            UUID userId) {
        TestRun run = testRunRepository.findById(runId)
                .filter(r -> r.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("TestRun", runId));

        if (request.resultIds().size() > 100) {
            throw new IllegalArgumentException("Cannot update more than 100 results at once");
        }

        List<TestResult> results = testResultRepository.findByIdInAndTestRunId(request.resultIds(), run.getId());
        if (results.size() != request.resultIds().size()) {
            throw new IllegalArgumentException("Some results do not belong to this test run");
        }

        for (TestResult result : results) {
            result.setStatus(request.status());
            if (request.cascadeSteps()) {
                result.getStepResults().forEach(sr -> sr.setStatus(request.status()));
            }
        }
        testResultRepository.saveAll(results);

        auditService.log(projectId, userId, AuditAction.STATUS_CHANGED,
                AuditEntityType.TEST_RUN, run.getId(), run.getName(),
                "Bulk set " + results.size() + " results to " + request.status());

        return new com.deanmanagement.testmanagement.project.internal.dto.testCase.BulkOperationResponse(
                results.size(), results.size() + " results updated to " + request.status());
    }

    @Transactional
    public StepResultResponse updateStepResult(UUID projectId, UUID runId, UUID resultId, UUID stepResultId,
                                               UpdateStepResultRequest request) {
        TestRun run = testRunRepository.findById(runId)
                .filter(r -> r.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("TestRun", runId));

        TestResult testResult = testResultRepository.findById(resultId)
                .filter(r -> r.getTestRun().getId().equals(run.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("TestResult", resultId));

        StepResult stepResult = stepResultRepository.findById(stepResultId)
                .filter(sr -> sr.getTestResult().getId().equals(testResult.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("StepResult", stepResultId));

        stepResult.setStatus(request.status());
        stepResult.setActualResult(request.actualResult());

        stepResult = stepResultRepository.save(stepResult);

        // Auto-compute worst status from all sibling step results and update parent
        TestResultStatus worstStatus = computeWorstStepStatus(testResult.getStepResults());
        if (testResult.getStatus() != worstStatus) {
            testResult.setStatus(worstStatus);
            testResultRepository.save(testResult);
        }

        return testRunMapper.toStepResultResponse(stepResult);
    }

    @Transactional
    public TestRunResponse setExecutor(UUID projectId, UUID id, UUID executorId) {
        TestRun run = testRunRepository.findById(id)
                .filter(r -> r.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("TestRun", id));

        run.setExecutor(requireProjectMember(projectId, executorId));

        run = testRunRepository.save(run);
        return testRunMapper.toResponse(run);
    }

    /**
     * Resolves caller-supplied test case ids <em>within this project</em>, refusing the whole call
     * if any id is unknown (PRD-027 §3.5).
     *
     * <p>This used to be {@code testCaseRepository.findAllById(ids)}, which is the third appearance
     * of the bug PRD-025 §8 fixed in {@code TestSuiteService.resolveTestCases}. Two things were
     * wrong with it. A run could be seeded with another project's cases, and {@code get_test_run}
     * (and the REST equivalent) then read their titles back to a caller with no access to them.
     * And {@code findAllById} <em>silently drops</em> ids it cannot find, so a run seeded with ten
     * ids of which three were typos was created with seven results and reported success — the
     * caller had no way to tell the difference between "these seven passed" and "the suite passed".
     *
     * <p>Failing the whole call rather than dropping the strays is the same choice
     * {@code resolveTestCases} makes, and for the same reason: a partial run that looks complete is
     * worse than no run.
     */
    private List<TestCase> resolveTestCases(UUID projectId, Set<UUID> testCaseIds) {
        List<TestCase> found = testCaseRepository.findByIdInAndProjectId(testCaseIds, projectId);
        if (found.size() != testCaseIds.size()) {
            Set<UUID> foundIds = found.stream().map(TestCase::getId).collect(Collectors.toSet());
            String missing = testCaseIds.stream()
                    .filter(id -> !foundIds.contains(id))
                    .map(UUID::toString)
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw new ResourceNotFoundException(
                    "TestCase(s) not found in this project: " + missing);
        }
        return found;
    }

    /**
     * Resolves a user who must already be a member of this project (PRD-027 §3.5).
     *
     * <p>{@code userService.findEntityById} resolves <em>any</em> user in the instance, so an
     * executor could be set to someone with no access to the project — who then sees the run in
     * their "My queue" widget, which reads by executor id and not by membership.
     *
     * <p>A non-member is reported as a missing user rather than a forbidden one: whether a given
     * UUID names a real account elsewhere in the instance is not this project's to disclose
     * (PRD-021).
     */
    private User requireProjectMember(UUID projectId, UUID userId) {
        if (!projectMemberRepository.existsByUserIdAndProjectId(userId, projectId)) {
            throw new ResourceNotFoundException("User", userId);
        }
        return userService.findEntityById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    private TestResultStatus computeWorstStepStatus(List<StepResult> stepResults) {
        if (stepResults.isEmpty()) {
            return TestResultStatus.PENDING;
        }
        for (TestResultStatus severity : SEVERITY_ORDER) {
            if (stepResults.stream().anyMatch(sr -> sr.getStatus() == severity)) {
                return severity;
            }
        }
        return TestResultStatus.PASSED;
    }

    private String computeWorstStatus(List<TestResult> results) {
        if (results.isEmpty()) {
            return TestResultStatus.PASSED.name();
        }
        for (TestResultStatus severity : SEVERITY_ORDER) {
            if (results.stream().anyMatch(r -> r.getStatus() == severity)) {
                return severity.name();
            }
        }
        return TestResultStatus.PASSED.name();
    }
}
