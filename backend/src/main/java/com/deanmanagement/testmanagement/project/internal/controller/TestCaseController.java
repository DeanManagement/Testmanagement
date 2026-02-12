package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.dto.testCase.CreateTestCaseRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.TestCaseResponse;
import com.deanmanagement.testmanagement.project.internal.dto.UpdateTestCaseRequest;
import com.deanmanagement.testmanagement.project.internal.service.TestCaseService;
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
@RequestMapping("/api/projects/{projectId}/test-cases")
@Tag(name = "Test Cases", description = "Test case management endpoints")
@RequiredArgsConstructor
public class TestCaseController {

    private final TestCaseService testCaseService;

    @GetMapping
    public List<TestCaseResponse> findAll(@PathVariable UUID projectId) {
        return testCaseService.findByProject(projectId);
    }

    @GetMapping("/{id}")
    public TestCaseResponse findById(@PathVariable UUID projectId, @PathVariable UUID id) {
        return testCaseService.findById(projectId, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TestCaseResponse create(@PathVariable UUID projectId,
                                   @Valid @RequestBody CreateTestCaseRequest request) {
        return testCaseService.create(projectId, request);
    }

    @PutMapping("/{id}")
    public TestCaseResponse update(@PathVariable UUID projectId,
                                   @PathVariable UUID id,
                                   @Valid @RequestBody UpdateTestCaseRequest request) {
        return testCaseService.update(projectId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID projectId, @PathVariable UUID id) {
        testCaseService.delete(projectId, id);
    }
}
