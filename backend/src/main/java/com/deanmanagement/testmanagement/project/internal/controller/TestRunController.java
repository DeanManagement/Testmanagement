package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.dto.CloneTestRunRequest;
import com.deanmanagement.testmanagement.project.internal.dto.CreateTestResultRequest;
import com.deanmanagement.testmanagement.project.internal.dto.CreateTestRunRequest;
import com.deanmanagement.testmanagement.project.internal.dto.StepResultResponse;
import com.deanmanagement.testmanagement.project.internal.dto.TestResultResponse;
import com.deanmanagement.testmanagement.project.internal.dto.TestRunResponse;
import com.deanmanagement.testmanagement.project.internal.dto.UpdateStepResultRequest;
import com.deanmanagement.testmanagement.project.internal.dto.UpdateTestResultRequest;
import com.deanmanagement.testmanagement.project.internal.dto.UpdateTestRunRequest;
import com.deanmanagement.testmanagement.project.internal.service.TestRunService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/test-runs")
@Tag(name = "Test Runs", description = "Test run execution and result management endpoints")
@RequiredArgsConstructor
public class TestRunController {

    private final TestRunService testRunService;

    @GetMapping
    public List<TestRunResponse> findAll(@PathVariable UUID projectId) {
        return testRunService.findByProject(projectId);
    }

    @GetMapping("/{id}")
    public TestRunResponse findById(@PathVariable UUID projectId, @PathVariable UUID id) {
        return testRunService.findById(projectId, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TestRunResponse create(@PathVariable UUID projectId,
                                  @Valid @RequestBody CreateTestRunRequest request) {
        return testRunService.create(projectId, request);
    }

    @PutMapping("/{id}")
    public TestRunResponse update(@PathVariable UUID projectId,
                                  @PathVariable UUID id,
                                  @Valid @RequestBody UpdateTestRunRequest request) {
        return testRunService.update(projectId, id, request);
    }

    @PostMapping("/{runId}/clone")
    @ResponseStatus(HttpStatus.CREATED)
    public TestRunResponse clone(@PathVariable UUID projectId, @PathVariable UUID runId,
                                 @Valid @RequestBody CloneTestRunRequest request) {
        return testRunService.cloneRun(projectId, runId, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID projectId, @PathVariable UUID id) {
        testRunService.delete(projectId, id);
    }

    @PostMapping("/{runId}/results")
    @ResponseStatus(HttpStatus.CREATED)
    public TestResultResponse addResult(@PathVariable UUID projectId,
                                        @PathVariable UUID runId,
                                        @Valid @RequestBody CreateTestResultRequest request) {
        return testRunService.addResult(projectId, runId, request);
    }

    @PutMapping("/{runId}/results/{resultId}")
    public TestResultResponse updateResult(@PathVariable UUID projectId,
                                           @PathVariable UUID runId,
                                           @PathVariable UUID resultId,
                                           @Valid @RequestBody UpdateTestResultRequest request) {
        return testRunService.updateResult(projectId, runId, resultId, request);
    }

    @PutMapping("/{runId}/results/{resultId}/steps/{stepResultId}")
    public StepResultResponse updateStepResult(@PathVariable UUID projectId,
                                               @PathVariable UUID runId,
                                               @PathVariable UUID resultId,
                                               @PathVariable UUID stepResultId,
                                               @Valid @RequestBody UpdateStepResultRequest request) {
        return testRunService.updateStepResult(projectId, runId, resultId, stepResultId, request);
    }
}
