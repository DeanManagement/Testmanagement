package com.deanmanagement.testmanagement.service;

import com.deanmanagement.testmanagement.dto.ExternalCreateTestRunRequest;
import com.deanmanagement.testmanagement.dto.ExternalStepResultRequest;
import com.deanmanagement.testmanagement.dto.ExternalTestResultRequest;
import com.deanmanagement.testmanagement.dto.TestRunMapper;
import com.deanmanagement.testmanagement.dto.TestRunResponse;
import com.deanmanagement.testmanagement.entity.Project;
import com.deanmanagement.testmanagement.entity.StepResult;
import com.deanmanagement.testmanagement.entity.TestCase;
import com.deanmanagement.testmanagement.entity.TestResult;
import com.deanmanagement.testmanagement.entity.TestRun;
import com.deanmanagement.testmanagement.entity.TestRunStatus;
import com.deanmanagement.testmanagement.entity.TestStep;
import com.deanmanagement.testmanagement.exception.ResourceNotFoundException;
import com.deanmanagement.testmanagement.repository.ProjectRepository;
import com.deanmanagement.testmanagement.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.repository.TestRunRepository;
import com.deanmanagement.testmanagement.repository.TestStepRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExternalTestRunService {

    private final TestRunRepository testRunRepository;
    private final ProjectRepository projectRepository;
    private final TestCaseRepository testCaseRepository;
    private final TestStepRepository testStepRepository;
    private final TestRunMapper testRunMapper;

    @Transactional
    public TestRunResponse createExternalRun(UUID projectId, ExternalCreateTestRunRequest request) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

        Instant now = Instant.now();

        TestRun run = new TestRun();
        run.setName(request.name());
        run.setEnvironment(request.environment());
        run.setProject(project);
        run.setStatus(TestRunStatus.COMPLETED);
        run.setStartTime(now);
        run.setEndTime(now);

        for (ExternalTestResultRequest resultReq : request.results()) {
            TestCase testCase = testCaseRepository.findById(resultReq.testCaseId())
                    .orElseThrow(() -> new ResourceNotFoundException("TestCase", resultReq.testCaseId()));

            TestResult result = new TestResult();
            result.setTestRun(run);
            result.setTestCase(testCase);
            result.setStatus(resultReq.status());
            result.setComment(resultReq.comment());
            result.setDefectLink(resultReq.defectLink());
            run.getResults().add(result);

            if (resultReq.stepResults() != null && !resultReq.stepResults().isEmpty()) {
                Map<UUID, TestStep> stepMap = testCase.getSteps().stream()
                        .collect(Collectors.toMap(TestStep::getId, s -> s));

                for (ExternalStepResultRequest stepReq : resultReq.stepResults()) {
                    TestStep step = stepMap.get(stepReq.testStepId());
                    if (step == null) {
                        throw new ResourceNotFoundException("TestStep", stepReq.testStepId());
                    }
                    StepResult stepResult = new StepResult();
                    stepResult.setTestResult(result);
                    stepResult.setTestStep(step);
                    stepResult.setStatus(stepReq.status());
                    stepResult.setActualResult(stepReq.actualResult());
                    result.getStepResults().add(stepResult);
                }
            } else {
                for (TestStep step : testCase.getSteps()) {
                    StepResult stepResult = new StepResult();
                    stepResult.setTestResult(result);
                    stepResult.setTestStep(step);
                    stepResult.setStatus(resultReq.status());
                    result.getStepResults().add(stepResult);
                }
            }
        }

        run = testRunRepository.save(run);
        return testRunMapper.toResponse(run);
    }
}
