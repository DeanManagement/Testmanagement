package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.ci.CucumberJsonParser;
import com.deanmanagement.testmanagement.project.internal.ci.JUnitXmlParser;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.ExternalCreateTestRunRequest;
import com.deanmanagement.testmanagement.project.internal.dto.TestRunResponse;
import com.deanmanagement.testmanagement.project.internal.entity.AllureReport;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.TestRun;
import com.deanmanagement.testmanagement.project.internal.service.AllureReportService;
import com.deanmanagement.testmanagement.project.internal.service.CiIngestionService;
import com.deanmanagement.testmanagement.project.internal.service.ExternalRefResolver;
import com.deanmanagement.testmanagement.project.internal.service.ExternalTestRunService;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/external/projects/{projectRef}/test-runs")
@Tag(name = "External Test Runs", description = "External API for submitting completed test runs")
@RequiredArgsConstructor
public class ExternalTestRunController {

    private final ExternalTestRunService externalTestRunService;
    private final AllureReportService allureReportService;
    private final ExternalRefResolver refResolver;
    private final CiIngestionService ciIngestionService;
    private final JUnitXmlParser jUnitXmlParser;
    private final CucumberJsonParser cucumberJsonParser;

    private static final int MAX_REPORT_BYTES = 10 * 1024 * 1024;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TestRunResponse create(@PathVariable String projectRef,
                                  @Valid @RequestBody ExternalCreateTestRunRequest request,
                                  @RequestParam(required = false) UUID pipelineRunId) {
        return externalTestRunService.createExternalRun(projectRef, request, pipelineRunId);
    }

    @PostMapping(value = "/junit", consumes = {MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_XML_VALUE})
    @ResponseStatus(HttpStatus.CREATED)
    public TestRunResponse importJunit(@PathVariable String projectRef,
                                       @RequestBody byte[] body,
                                       @RequestParam(required = false) String runName,
                                       @RequestParam(required = false) String environment,
                                       @RequestParam(required = false) UUID testPlanId,
                                       @RequestParam(required = false) UUID pipelineRunId) {
        checkSize(body);
        // With a pipelineRunId and no explicit name the run is named after its workflow (PRD-024).
        String name = runName != null ? runName : pipelineRunId != null ? null : "JUnit import";
        return ciIngestionService.ingest(projectRef, name, environment, testPlanId,
                jUnitXmlParser.parse(body), pipelineRunId);
    }

    @PostMapping(value = "/cucumber", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public TestRunResponse importCucumber(@PathVariable String projectRef,
                                          @RequestBody byte[] body,
                                          @RequestParam(required = false) String runName,
                                          @RequestParam(required = false) String environment,
                                          @RequestParam(required = false) UUID testPlanId,
                                          @RequestParam(required = false) UUID pipelineRunId) {
        checkSize(body);
        String name = runName != null ? runName : pipelineRunId != null ? null : "Cucumber import";
        return ciIngestionService.ingest(projectRef, name, environment, testPlanId,
                cucumberJsonParser.parse(body), pipelineRunId);
    }

    private void checkSize(byte[] body) {
        if (body == null || body.length == 0) {
            throw new IllegalArgumentException("Empty report body");
        }
        if (body.length > MAX_REPORT_BYTES) {
            throw new IllegalArgumentException("Report exceeds the maximum allowed size");
        }
    }

    @PostMapping(value = "/{testRunRef}/allure-report", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, UUID> uploadAllureReport(@PathVariable String projectRef,
                                                @PathVariable String testRunRef,
                                                @RequestParam MultipartFile file) throws IOException {
        Project project = refResolver.resolveProject(projectRef);
        TestRun testRun = refResolver.resolveTestRun(testRunRef);
        // The API-key filter only checks the project segment of the URL, so without this a key
        // scoped to project A could attach a report to a run belonging to project B by naming A
        // in the path. Reported as not-found rather than forbidden: to this caller the run in
        // another project may as well not exist.
        if (!testRun.getProject().getId().equals(project.getId())) {
            throw new ResourceNotFoundException("TestRun", testRunRef);
        }
        AllureReport report = allureReportService.upload(
                testRun.getId(),
                file.getOriginalFilename(),
                file.getBytes()
        );
        return Map.of("id", report.getId());
    }
}
