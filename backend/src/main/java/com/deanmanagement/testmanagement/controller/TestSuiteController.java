package com.deanmanagement.testmanagement.controller;

import com.deanmanagement.testmanagement.dto.CreateTestSuiteRequest;
import com.deanmanagement.testmanagement.dto.TestSuiteResponse;
import com.deanmanagement.testmanagement.dto.UpdateTestSuiteRequest;
import com.deanmanagement.testmanagement.service.TestSuiteService;
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
@RequestMapping("/api/projects/{projectId}/test-suites")
@Tag(name = "Test Suites", description = "Test suite management endpoints")
@RequiredArgsConstructor
public class TestSuiteController {

    private final TestSuiteService testSuiteService;

    @GetMapping
    public List<TestSuiteResponse> findAll(@PathVariable UUID projectId) {
        return testSuiteService.findByProject(projectId);
    }

    @GetMapping("/{id}")
    public TestSuiteResponse findById(@PathVariable UUID projectId, @PathVariable UUID id) {
        return testSuiteService.findById(projectId, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TestSuiteResponse create(@PathVariable UUID projectId,
                                    @Valid @RequestBody CreateTestSuiteRequest request) {
        return testSuiteService.create(projectId, request);
    }

    @PutMapping("/{id}")
    public TestSuiteResponse update(@PathVariable UUID projectId,
                                    @PathVariable UUID id,
                                    @Valid @RequestBody UpdateTestSuiteRequest request) {
        return testSuiteService.update(projectId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID projectId, @PathVariable UUID id) {
        testSuiteService.delete(projectId, id);
    }
}
