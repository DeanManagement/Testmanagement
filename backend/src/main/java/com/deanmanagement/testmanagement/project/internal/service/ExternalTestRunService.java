package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.testrun.ExternalCreateTestRunRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.ExternalStepResultRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.ExternalTestResultRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.TestRunMapper;
import com.deanmanagement.testmanagement.project.internal.dto.TestRunResponse;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.StepResult;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestResult;
import com.deanmanagement.testmanagement.project.internal.entity.TestRun;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestStep;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExternalTestRunService {

    private final TestRunRepository testRunRepository;
    private final ProjectRepository projectRepository;
    private final TestCaseRepository testCaseRepository;
    private final TestRunMapper testRunMapper;

    @Transactional
    public TestRunResponse createExternalRun(String projectKey, ExternalCreateTestRunRequest request) {
        Project project = projectRepository.findByKey(projectKey)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectKey));

        Instant now = Instant.now();

        TestRun run = new TestRun();
        run.setName(request.name());
        run.setEnvironment(request.environment());
        run.setProject(project);
        run.setStatus(TestRunStatus.COMPLETED);
        run.setStartTime(now);
        run.setEndTime(now);
        run.setKey(project.getKey() + "-Run-" + project.getNextTestRunNumber());
        project.setNextTestRunNumber(project.getNextTestRunNumber() + 1);

        for (ExternalTestResultRequest resultReq : request.results()) {
            TestCase testCase = testCaseRepository.findByKeyAndProjectId(resultReq.testCaseKey(), project.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("TestCase", resultReq.testCaseKey()));

            TestResult result = new TestResult();
            result.setTestRun(run);
            result.setTestCase(testCase);
            result.setStatus(resultReq.status());
            result.setComment(resultReq.comment());
            result.setDefectLink(resultReq.defectLink());
            run.getResults().add(result);

            if (resultReq.stepResults() != null && !resultReq.stepResults().isEmpty()) {
                Map<Integer, TestStep> stepMap = testCase.getSteps().stream()
                        .collect(Collectors.toMap(TestStep::getOrderIndex, s -> s));

                for (ExternalStepResultRequest stepReq : resultReq.stepResults()) {
                    TestStep step = stepMap.get(stepReq.stepIndex() - 1);
                    if (step == null) {
                        throw new ResourceNotFoundException("TestStep", "index " + stepReq.stepIndex());
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
