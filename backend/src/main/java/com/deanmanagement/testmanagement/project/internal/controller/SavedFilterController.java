package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.access.RequireProjectRole;
import com.deanmanagement.testmanagement.project.internal.dto.savedfilter.CreateSavedFilterRequest;
import com.deanmanagement.testmanagement.project.internal.dto.savedfilter.SavedFilterResponse;
import com.deanmanagement.testmanagement.project.internal.dto.savedfilter.UpdateSavedFilterRequest;
import com.deanmanagement.testmanagement.project.internal.entity.SavedFilterView;
import com.deanmanagement.testmanagement.project.internal.service.SavedFilterService;
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

/** Saved list filters (PRD-053 §3). Any member may save and share; see SavedFilterService for edits. */
@RestController
@RequestMapping("/api/projects/{projectId}/saved-filters")
@Tag(name = "Saved Filters", description = "Saved and shared list filters")
@RequiredArgsConstructor
public class SavedFilterController {

    private final SavedFilterService savedFilterService;

    @GetMapping
    @RequireProjectRole
    public List<SavedFilterResponse> findVisible(@PathVariable UUID projectId, @RequestParam SavedFilterView view,
                                                 Authentication authentication) {
        return savedFilterService.findVisible(projectId, view, userId(authentication));
    }

    @PostMapping
    @RequireProjectRole
    @ResponseStatus(HttpStatus.CREATED)
    public SavedFilterResponse create(@PathVariable UUID projectId, @Valid @RequestBody CreateSavedFilterRequest request,
                                      Authentication authentication) {
        return savedFilterService.create(projectId, request, userId(authentication));
    }

    @PutMapping("/{id}")
    @RequireProjectRole
    public SavedFilterResponse update(@PathVariable UUID projectId, @PathVariable UUID id,
                                      @Valid @RequestBody UpdateSavedFilterRequest request,
                                      Authentication authentication) {
        return savedFilterService.update(projectId, id, request, userId(authentication));
    }

    @DeleteMapping("/{id}")
    @RequireProjectRole
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID projectId, @PathVariable UUID id, Authentication authentication) {
        savedFilterService.delete(projectId, id, userId(authentication));
    }

    private static UUID userId(Authentication authentication) {
        return authentication != null ? UUID.fromString(authentication.getName()) : null;
    }
}
