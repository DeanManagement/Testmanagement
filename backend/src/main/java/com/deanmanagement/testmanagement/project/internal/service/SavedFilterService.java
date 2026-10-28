package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.access.ProjectAccessService;
import com.deanmanagement.testmanagement.project.internal.dto.savedfilter.CreateSavedFilterRequest;
import com.deanmanagement.testmanagement.project.internal.dto.savedfilter.SavedFilterResponse;
import com.deanmanagement.testmanagement.project.internal.dto.savedfilter.UpdateSavedFilterRequest;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.SavedFilter;
import com.deanmanagement.testmanagement.project.internal.entity.SavedFilterView;
import com.deanmanagement.testmanagement.project.internal.repository.SavedFilterRepository;
import com.deanmanagement.testmanagement.shared.exception.ForbiddenException;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Saved list filters (PRD-053 §3). A filter is private to its owner until shared with the project;
 * its owner may always change it, and a project ADMIN may change a shared one. Someone else's
 * private filter is reported as not found, not forbidden, so its existence does not leak.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SavedFilterService {

    private final SavedFilterRepository repository;
    private final ProjectAccessService accessService;

    public List<SavedFilterResponse> findVisible(UUID projectId, SavedFilterView view, UUID userId) {
        boolean admin = isProjectAdmin(userId, projectId);
        return repository.findVisible(projectId, view, requireUser(userId)).stream()
                .map(f -> toResponse(f, userId, admin))
                .toList();
    }

    @Transactional
    public SavedFilterResponse create(UUID projectId, CreateSavedFilterRequest request, UUID userId) {
        SavedFilter filter = new SavedFilter();
        filter.setProjectId(projectId);
        filter.setOwnerId(requireUser(userId));
        filter.setName(request.name().trim());
        filter.setView(request.view());
        filter.setQueryString(stripQuestionMark(request.queryString()));
        filter.setShared(request.shared());
        return toResponse(repository.save(filter), userId, isProjectAdmin(userId, projectId));
    }

    @Transactional
    public SavedFilterResponse update(UUID projectId, UUID id, UpdateSavedFilterRequest request, UUID userId) {
        SavedFilter filter = requireEditable(projectId, id, userId);
        filter.setName(request.name().trim());
        filter.setShared(request.shared());
        return toResponse(repository.save(filter), userId, isProjectAdmin(userId, projectId));
    }

    @Transactional
    public void delete(UUID projectId, UUID id, UUID userId) {
        repository.delete(requireEditable(projectId, id, userId));
    }

    private SavedFilter requireEditable(UUID projectId, UUID id, UUID userId) {
        requireUser(userId);
        SavedFilter filter = repository.findByIdAndProjectId(id, projectId)
                .filter(f -> f.isShared() || f.getOwnerId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("SavedFilter", id));
        if (!isEditable(filter, userId, isProjectAdmin(userId, projectId))) {
            throw new ForbiddenException("Only its owner or a project ADMIN can change a shared filter");
        }
        return filter;
    }

    static boolean isEditable(SavedFilter filter, UUID userId, boolean projectAdmin) {
        return filter.getOwnerId().equals(userId) || (filter.isShared() && projectAdmin);
    }

    private boolean isProjectAdmin(UUID userId, UUID projectId) {
        return userId != null && accessService.requireMember(userId, projectId) == ProjectRole.ADMIN;
    }

    /** A saved filter belongs to a person, so an unauthenticated (dev permit-all) caller cannot keep one. */
    private static UUID requireUser(UUID userId) {
        if (userId == null) {
            throw new ForbiddenException("Saved filters need a signed-in user");
        }
        return userId;
    }

    private static String stripQuestionMark(String queryString) {
        String trimmed = queryString.trim();
        return trimmed.startsWith("?") ? trimmed.substring(1) : trimmed;
    }

    private static SavedFilterResponse toResponse(SavedFilter f, UUID userId, boolean projectAdmin) {
        return new SavedFilterResponse(f.getId(), f.getName(), f.getView(), f.getQueryString(), f.isShared(),
                f.getOwnerId().equals(userId), isEditable(f, userId, projectAdmin));
    }
}
