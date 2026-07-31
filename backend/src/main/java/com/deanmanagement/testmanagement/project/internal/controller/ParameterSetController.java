package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.access.RequireProjectRole;
import com.deanmanagement.testmanagement.project.internal.dto.parameter.ParameterSetResponse;
import com.deanmanagement.testmanagement.project.internal.dto.parameter.SaveParameterSetRequest;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.service.ParameterSetService;
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

/**
 * Parameter sets on a test case (PRD-015 §3.3). Writing needs TESTER, matching who may edit the
 * case itself — a set changes what future runs execute.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/test-cases/{testCaseId}/parameter-sets")
@Tag(name = "Parameter Sets", description = "Data-driven parameter sets for a test case")
@RequiredArgsConstructor
public class ParameterSetController {

    private final ParameterSetService parameterSetService;

    @GetMapping
    @RequireProjectRole
    public List<ParameterSetResponse> list(@PathVariable UUID projectId,
                                           @PathVariable UUID testCaseId) {
        return parameterSetService.list(projectId, testCaseId);
    }

    @PostMapping
    @RequireProjectRole(ProjectRole.TESTER)
    @ResponseStatus(HttpStatus.CREATED)
    public ParameterSetResponse create(@PathVariable UUID projectId,
                                       @PathVariable UUID testCaseId,
                                       @Valid @RequestBody SaveParameterSetRequest request) {
        return parameterSetService.create(projectId, testCaseId, request);
    }

    @PutMapping("/{id}")
    @RequireProjectRole(ProjectRole.TESTER)
    public ParameterSetResponse update(@PathVariable UUID projectId,
                                       @PathVariable UUID testCaseId,
                                       @PathVariable UUID id,
                                       @Valid @RequestBody SaveParameterSetRequest request) {
        return parameterSetService.update(projectId, testCaseId, id, request);
    }

    @DeleteMapping("/{id}")
    @RequireProjectRole(ProjectRole.TESTER)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID projectId,
                       @PathVariable UUID testCaseId,
                       @PathVariable UUID id) {
        parameterSetService.delete(projectId, testCaseId, id);
    }
}
