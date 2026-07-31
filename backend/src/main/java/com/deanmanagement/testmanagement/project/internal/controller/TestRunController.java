package com.deanmanagement.testmanagement.project.internal.controller;
import com.deanmanagement.testmanagement.project.internal.access.RequireProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;

import com.deanmanagement.testmanagement.project.internal.dto.CompletionInfoResponse;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.CloneTestRunRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.CreateTestResultRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.CreateTestRunRequest;
import com.deanmanagement.testmanagement.project.internal.dto.StepResultResponse;
import com.deanmanagement.testmanagement.project.internal.dto.TestResultResponse;
import com.deanmanagement.testmanagement.project.internal.dto.report.TestRunReportResponse;
import com.deanmanagement.testmanagement.project.internal.dto.TestRunResponse;
import com.deanmanagement.testmanagement.project.internal.dto.TestRunSummaryResponse;
import com.deanmanagement.testmanagement.project.internal.dto.UpdateStepResultRequest;
import com.deanmanagement.testmanagement.project.internal.dto.UpdateTestResultRequest;
import com.deanmanagement.testmanagement.project.internal.dto.UpdateTestRunRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.SetExecutorRequest;
import com.deanmanagement.testmanagement.project.internal.dto.filter.TestRunListFilter;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import com.deanmanagement.testmanagement.project.internal.service.PdfReportService;
import com.deanmanagement.testmanagement.project.internal.service.TestRunService;
import com.deanmanagement.testmanagement.shared.PageableUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/test-runs")
@Tag(name = "Test Runs", description = "Test run execution and result management endpoints")
@RequiredArgsConstructor
public class TestRunController {

    private final TestRunService testRunService;
    private final PdfReportService pdfReportService;

    @GetMapping
    @RequireProjectRole
    public Page<TestRunSummaryResponse> findAll(@PathVariable UUID projectId,
                                         @RequestParam(required = false) String q,
                                         @RequestParam(required = false) List<TestRunStatus> status,
                                         @RequestParam(required = false) UUID testPlanId,
                                         @RequestParam(required = false) UUID executorId,
                                         @RequestParam(required = false)
                                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startedAfter,
                                         @PageableDefault(size = PageableUtils.DEFAULT_SIZE) Pageable pageable) {
        TestRunListFilter filter = new TestRunListFilter(q, status, testPlanId, executorId, startedAfter);
        return testRunService.findByProject(projectId, filter, PageableUtils.normalize(pageable));
    }

    @GetMapping("/{id}")
    @RequireProjectRole
    public TestRunResponse findById(@PathVariable UUID projectId, @PathVariable UUID id) {
        return testRunService.findById(projectId, id);
    }

    @GetMapping("/{id}/report")
    @RequireProjectRole
    public TestRunReportResponse getReport(@PathVariable UUID projectId, @PathVariable UUID id) {
        return testRunService.getReport(projectId, id);
    }

    @GetMapping("/{id}/report/pdf")
    @RequireProjectRole
    public ResponseEntity<byte[]> getReportPdf(@PathVariable UUID projectId, @PathVariable UUID id) {
        byte[] pdf = pdfReportService.generateTestRunReport(projectId, id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "test-run-report.pdf");
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }

    @GetMapping("/{id}/completion-info")
    @RequireProjectRole
    public CompletionInfoResponse getCompletionInfo(@PathVariable UUID projectId, @PathVariable UUID id) {
        return testRunService.getCompletionInfo(projectId, id);
    }

    @PostMapping
    @RequireProjectRole(ProjectRole.TESTER)
    @ResponseStatus(HttpStatus.CREATED)
    public TestRunResponse create(@PathVariable UUID projectId,
                                  @Valid @RequestBody CreateTestRunRequest request,
                                  Authentication authentication) {
        UUID currentUserId = authentication != null ? UUID.fromString(authentication.getName()) : null;
        return testRunService.create(projectId, request, currentUserId);
    }

    @PutMapping("/{id}")
    @RequireProjectRole(ProjectRole.TESTER)
    public TestRunResponse update(@PathVariable UUID projectId,
                                  @PathVariable UUID id,
                                  @Valid @RequestBody UpdateTestRunRequest request,
                                  Authentication authentication) {
        UUID currentUserId = authentication != null ? UUID.fromString(authentication.getName()) : null;
        return testRunService.update(projectId, id, request, currentUserId);
    }

    @PutMapping("/{id}/executor")
    @RequireProjectRole(ProjectRole.TESTER)
    public TestRunResponse setExecutor(@PathVariable UUID projectId,
                                       @PathVariable UUID id,
                                       @Valid @RequestBody SetExecutorRequest request) {
        return testRunService.setExecutor(projectId, id, request.executorId());
    }

    @PostMapping("/{runId}/clone")
    @RequireProjectRole(ProjectRole.TESTER)
    @ResponseStatus(HttpStatus.CREATED)
    public TestRunResponse clone(@PathVariable UUID projectId, @PathVariable UUID runId,
                                 @Valid @RequestBody CloneTestRunRequest request,
                                 Authentication authentication) {
        UUID currentUserId = authentication != null ? UUID.fromString(authentication.getName()) : null;
        return testRunService.cloneRun(projectId, runId, request, currentUserId);
    }

    @DeleteMapping("/{id}")
    @RequireProjectRole(ProjectRole.TESTER)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID projectId, @PathVariable UUID id,
                       Authentication authentication) {
        UUID currentUserId = authentication != null ? UUID.fromString(authentication.getName()) : null;
        testRunService.delete(projectId, id, currentUserId);
    }

    @PostMapping("/{runId}/results")
    @RequireProjectRole(ProjectRole.TESTER)
    @ResponseStatus(HttpStatus.CREATED)
    public TestResultResponse addResult(@PathVariable UUID projectId,
                                        @PathVariable UUID runId,
                                        @Valid @RequestBody CreateTestResultRequest request) {
        return testRunService.addResult(projectId, runId, request);
    }

    @PutMapping("/{runId}/results/{resultId}")
    @RequireProjectRole(ProjectRole.TESTER)
    public TestResultResponse updateResult(@PathVariable UUID projectId,
                                           @PathVariable UUID runId,
                                           @PathVariable UUID resultId,
                                           @Valid @RequestBody UpdateTestResultRequest request) {
        return testRunService.updateResult(projectId, runId, resultId, request);
    }

    @PutMapping("/{runId}/results/{resultId}/steps/{stepResultId}")
    @RequireProjectRole(ProjectRole.TESTER)
    public StepResultResponse updateStepResult(@PathVariable UUID projectId,
                                               @PathVariable UUID runId,
                                               @PathVariable UUID resultId,
                                               @PathVariable UUID stepResultId,
                                               @Valid @RequestBody UpdateStepResultRequest request) {
        return testRunService.updateStepResult(projectId, runId, resultId, stepResultId, request);
    }

    @PostMapping("/{runId}/results/bulk-status")
    @RequireProjectRole(ProjectRole.TESTER)
    public com.deanmanagement.testmanagement.project.internal.dto.testCase.BulkOperationResponse bulkResultStatus(
            @PathVariable UUID projectId,
            @PathVariable UUID runId,
            @Valid @RequestBody com.deanmanagement.testmanagement.project.internal.dto.testrun.BulkResultStatusRequest request,
            Authentication authentication) {
        UUID userId = authentication != null ? UUID.fromString(authentication.getName()) : null;
        return testRunService.bulkUpdateResultStatus(projectId, runId, request, userId);
    }
}
