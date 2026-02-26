package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.dto.testrun.ExternalCreateTestRunRequest;
import com.deanmanagement.testmanagement.project.internal.dto.TestRunResponse;
import com.deanmanagement.testmanagement.project.internal.entity.AllureReport;
import com.deanmanagement.testmanagement.project.internal.entity.TestRun;
import com.deanmanagement.testmanagement.project.internal.repository.TestRunRepository;
import com.deanmanagement.testmanagement.project.internal.service.AllureReportService;
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
@RequestMapping("/api/external/projects/{projectKey}/test-runs")
@Tag(name = "External Test Runs", description = "External API for submitting completed test runs")
@RequiredArgsConstructor
public class ExternalTestRunController {

    private final ExternalTestRunService externalTestRunService;
    private final AllureReportService allureReportService;
    private final TestRunRepository testRunRepository;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TestRunResponse create(@PathVariable String projectKey,
                                  @Valid @RequestBody ExternalCreateTestRunRequest request) {
        return externalTestRunService.createExternalRun(projectKey, request);
    }

    @PostMapping(value = "/{testRunKey}/allure-report", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, UUID> uploadAllureReport(@PathVariable String projectKey,
                                                @PathVariable String testRunKey,
                                                @RequestParam MultipartFile file) throws IOException {
        TestRun testRun = testRunRepository.findByKey(testRunKey)
                .orElseThrow(() -> new ResourceNotFoundException("TestRun", testRunKey));
        AllureReport report = allureReportService.upload(
                testRun.getId(),
                file.getOriginalFilename(),
                file.getBytes()
        );
        return Map.of("id", report.getId());
    }
}
