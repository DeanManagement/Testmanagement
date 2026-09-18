package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.access.RequireProjectRole;
import com.deanmanagement.testmanagement.project.internal.dto.customField.CreateCustomFieldRequest;
import com.deanmanagement.testmanagement.project.internal.dto.customField.CustomFieldResponse;
import com.deanmanagement.testmanagement.project.internal.dto.customField.UpdateCustomFieldRequest;
import com.deanmanagement.testmanagement.project.internal.entity.CustomFieldEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.service.CustomFieldService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

import java.util.List;
import java.util.UUID;

/**
 * Custom field definitions (PRD-035). Any member reads them; only ADMIN curates them. Values
 * travel on the test case, test run and bug report endpoints and inherit their roles.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/custom-fields")
@Tag(name = "Custom Fields", description = "Per-project custom field definitions")
@RequiredArgsConstructor
public class CustomFieldController {

    private final CustomFieldService customFieldService;

    @GetMapping
    @RequireProjectRole
    public List<CustomFieldResponse> list(@PathVariable UUID projectId,
                                          @RequestParam(required = false) CustomFieldEntityType entityType) {
        return customFieldService.list(projectId, entityType);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequireProjectRole(ProjectRole.ADMIN)
    public CustomFieldResponse create(@PathVariable UUID projectId,
                                      @Valid @RequestBody CreateCustomFieldRequest request,
                                      Authentication authentication) {
        return customFieldService.create(projectId, request, actor(authentication));
    }

    @PutMapping("/{id}")
    @RequireProjectRole(ProjectRole.ADMIN)
    public CustomFieldResponse update(@PathVariable UUID projectId, @PathVariable UUID id,
                                      @Valid @RequestBody UpdateCustomFieldRequest request,
                                      Authentication authentication) {
        return customFieldService.update(projectId, id, request, actor(authentication));
    }

    @PostMapping("/{id}/archive")
    @RequireProjectRole(ProjectRole.ADMIN)
    public CustomFieldResponse archive(@PathVariable UUID projectId, @PathVariable UUID id,
                                       Authentication authentication) {
        return customFieldService.archive(projectId, id, actor(authentication));
    }

    @PostMapping("/{id}/unarchive")
    @RequireProjectRole(ProjectRole.ADMIN)
    public CustomFieldResponse unarchive(@PathVariable UUID projectId, @PathVariable UUID id,
                                         Authentication authentication) {
        return customFieldService.unarchive(projectId, id, actor(authentication));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequireProjectRole(ProjectRole.ADMIN)
    public void delete(@PathVariable UUID projectId, @PathVariable UUID id,
                       @RequestParam(defaultValue = "false") boolean force,
                       Authentication authentication) {
        if (force) {
            customFieldService.forceDelete(projectId, id, actor(authentication));
        } else {
            customFieldService.delete(projectId, id, actor(authentication));
        }
    }

    private static UUID actor(Authentication authentication) {
        return authentication != null ? UUID.fromString(authentication.getName()) : null;
    }
}
