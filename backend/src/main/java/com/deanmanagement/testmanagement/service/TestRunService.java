package com.deanmanagement.testmanagement.service;

import com.deanmanagement.testmanagement.dto.CloneTestRunRequest;
import com.deanmanagement.testmanagement.dto.CreateTestResultRequest;
import com.deanmanagement.testmanagement.dto.CreateTestRunRequest;
import com.deanmanagement.testmanagement.dto.StepResultResponse;
import com.deanmanagement.testmanagement.dto.TestRunMapper;
import com.deanmanagement.testmanagement.dto.TestRunResponse;
import com.deanmanagement.testmanagement.dto.TestResultResponse;
import com.deanmanagement.testmanagement.dto.UpdateStepResultRequest;
import com.deanmanagement.testmanagement.dto.UpdateTestResultRequest;
import com.deanmanagement.testmanagement.dto.UpdateTestRunRequest;
import com.deanmanagement.testmanagement.entity.Project;
import com.deanmanagement.testmanagement.entity.StepResult;
import com.deanmanagement.testmanagement.entity.TestCase;
import com.deanmanagement.testmanagement.entity.TestResult;
import com.deanmanagement.testmanagement.entity.TestResultStatus;
import com.deanmanagement.testmanagement.entity.TestRun;
import com.deanmanagement.testmanagement.entity.TestRunStatus;
import com.deanmanagement.testmanagement.entity.TestStep;
import com.deanmanagement.testmanagement.exception.ResourceNotFoundException;
import com.deanmanagement.testmanagement.repository.ProjectRepository;
import com.deanmanagement.testmanagement.repository.StepResultRepository;
import com.deanmanagement.testmanagement.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.repository.TestResultRepository;
import com.deanmanagement.testmanagement.repository.TestRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TestRunService {

    private final TestRunRepository testRunRepository;
    private final TestResultRepository testResultRepository;
    private final StepResultRepository stepResultRepository;
    private final ProjectRepository projectRepository;
    private final TestCaseRepository testCaseRepository;
    private final TestRunMapper testRunMapper;

    public List<TestRunResponse> findByProject(UUID projectId) {
        return testRunRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(testRunMapper::toResponse)
                .toList();
    }

    public TestRunResponse findById(UUID projectId, UUID id) {
        TestRun run = testRunRepository.findById(id)
                .filter(r -> r.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("TestRun", id));
        return testRunMapper.toResponse(run);
    }

    @Transactional
    public TestRunResponse create(UUID projectId, CreateTestRunRequest request) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

        TestRun run = testRunMapper.toEntity(request);
        run.setProject(project);
        run.setStatus(TestRunStatus.PLANNED);

        // If test case IDs provided, create pending results for each
        if (request.testCaseIds() != null && !request.testCaseIds().isEmpty()) {
            List<TestCase> testCases = testCaseRepository.findAllById(request.testCaseIds());
            for (TestCase tc : testCases) {
                TestResult result = new TestResult();
                result.setTestRun(run);
                result.setTestCase(tc);
                result.setStatus(TestResultStatus.PENDING);
                run.getResults().add(result);

                // Create step results for each test step
                for (TestStep step : tc.getSteps()) {
                    StepResult stepResult = new StepResult();
                    stepResult.setTestResult(result);
                    stepResult.setTestStep(step);
                    stepResult.setStatus(TestResultStatus.PENDING);
                    result.getStepResults().add(stepResult);
                }
            }
        }

        run = testRunRepository.save(run);
        return testRunMapper.toResponse(run);
    }

    @Transactional
    public TestRunResponse cloneRun(UUID projectId, UUID runId, CloneTestRunRequest request) {
        TestRun source = testRunRepository.findById(runId)
                .filter(r -> r.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("TestRun", runId));

        Set<UUID> testCaseIds = source.getResults().stream()
                .map(r -> r.getTestCase().getId())
                .collect(Collectors.toSet());

        CreateTestRunRequest createRequest = new CreateTestRunRequest(
                request.name(),
                request.environment(),
                testCaseIds
        );
        return create(projectId, createRequest);
    }

    @Transactional
    public TestRunResponse update(UUID projectId, UUID id, UpdateTestRunRequest request) {
        TestRun run = testRunRepository.findById(id)
                .filter(r -> r.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("TestRun", id));

        run.setName(request.name());
        run.setEnvironment(request.environment());

        if (request.status() != null) {
            TestRunStatus oldStatus = run.getStatus();
            run.setStatus(request.status());

            if (request.status() == TestRunStatus.IN_PROGRESS && oldStatus == TestRunStatus.PLANNED) {
                run.setStartTime(Instant.now());
            } else if (request.status() == TestRunStatus.COMPLETED || request.status() == TestRunStatus.ABORTED) {
                run.setEndTime(Instant.now());
            }
        }

        run = testRunRepository.save(run);
        return testRunMapper.toResponse(run);
    }

    @Transactional
    public void delete(UUID projectId, UUID id) {
        TestRun run = testRunRepository.findById(id)
                .filter(r -> r.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("TestRun", id));
        testRunRepository.delete(run);
    }

    @Transactional
    public TestResultResponse addResult(UUID projectId, UUID runId, CreateTestResultRequest request) {
        TestRun run = testRunRepository.findById(runId)
                .filter(r -> r.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("TestRun", runId));

        TestCase testCase = testCaseRepository.findById(request.testCaseId())
                .orElseThrow(() -> new ResourceNotFoundException("TestCase", request.testCaseId()));

        TestResult result = new TestResult();
        result.setTestRun(run);
        result.setTestCase(testCase);
        result.setStatus(request.status());
        result.setComment(request.comment());
        result.setDefectLink(request.defectLink());

        result = testResultRepository.save(result);
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

        result = testResultRepository.save(result);
        return testRunMapper.toResultResponse(result);
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
        return testRunMapper.toStepResultResponse(stepResult);
    }
}
