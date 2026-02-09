package com.deanmanagement.testmanagement.controller;

import com.deanmanagement.testmanagement.dto.ExternalCreateTestRunRequest;
import com.deanmanagement.testmanagement.dto.TestRunResponse;
import com.deanmanagement.testmanagement.service.ExternalTestRunService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/external/projects/{projectId}/test-runs")
@Tag(name = "External Test Runs", description = "External API for submitting completed test runs")
@RequiredArgsConstructor
public class ExternalTestRunController {

    private final ExternalTestRunService externalTestRunService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TestRunResponse create(@PathVariable UUID projectId,
                                  @Valid @RequestBody ExternalCreateTestRunRequest request) {
        return externalTestRunService.createExternalRun(projectId, request);
    }
}
