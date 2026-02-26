package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.project.CreateProjectRequest;
import com.deanmanagement.testmanagement.project.internal.dto.project.ProjectMapper;
import com.deanmanagement.testmanagement.project.internal.dto.project.ProjectResponse;
import com.deanmanagement.testmanagement.project.internal.dto.project.UpdateProjectRequest;
import com.deanmanagement.testmanagement.project.internal.entity.AuditAction;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMapper projectMapper;
    private final AuditService auditService;

    public List<ProjectResponse> findAll(UUID userId, boolean isSystemAdmin) {
        if (isSystemAdmin) {
            return projectRepository.findAll().stream()
                    .map(projectMapper::toResponse)
                    .toList();
        }
        return projectRepository.findByMembersUserId(userId).stream()
                .map(projectMapper::toResponse)
                .toList();
    }

    public ProjectResponse findById(UUID id) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project", id));
        return projectMapper.toResponse(project);
    }

    @Transactional
    public ProjectResponse create(CreateProjectRequest request, UUID userId) {
        Project project = projectMapper.toEntity(request);
        project.setKey(generateKey(request.name()));
        project = projectRepository.save(project);
        auditService.log(project.getId(), userId, AuditAction.CREATED,
                AuditEntityType.PROJECT, project.getId(), project.getName(), null);
        return projectMapper.toResponse(project);
    }

    @Transactional
    public ProjectResponse update(UUID id, UpdateProjectRequest request, UUID userId) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project", id));
        projectMapper.updateEntity(request, project);
        project = projectRepository.save(project);
        auditService.log(project.getId(), userId, AuditAction.UPDATED,
                AuditEntityType.PROJECT, project.getId(), project.getName(), null);
        return projectMapper.toResponse(project);
    }

    @Transactional
    public void delete(UUID id, UUID userId) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project", id));
        auditService.log(project.getId(), userId, AuditAction.DELETED,
                AuditEntityType.PROJECT, project.getId(), project.getName(), null);
        projectRepository.delete(project);
    }

    public List<ProjectResponse> searchByKey(String key) {
        return projectRepository.findByKeyContainingIgnoreCase(key).stream()
                .map(projectMapper::toResponse)
                .toList();
    }

    String generateKey(String name) {
        String sanitized = name.replaceAll("[^a-zA-Z0-9\\s]", "").trim();
        if (sanitized.isEmpty()) {
            sanitized = "PRJ";
        }

        String[] words = sanitized.split("\\s+");
        String baseKey;
        if (words.length > 1) {
            StringBuilder sb = new StringBuilder();
            for (String word : words) {
                if (!word.isEmpty()) {
                    sb.append(Character.toUpperCase(word.charAt(0)));
                }
                if (sb.length() >= 10) break;
            }
            baseKey = sb.toString();
        } else {
            String word = words[0].toUpperCase();
            baseKey = word.length() >= 3 ? word.substring(0, 3) : word;
        }

        if (baseKey.length() < 2) {
            baseKey = (baseKey + "XX").substring(0, 2);
        }

        if (!projectRepository.existsByKey(baseKey)) {
            return baseKey;
        }
        for (int suffix = 2; suffix <= 999; suffix++) {
            String candidate = baseKey + suffix;
            if (!projectRepository.existsByKey(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate unique project key for: " + name);
    }
}
