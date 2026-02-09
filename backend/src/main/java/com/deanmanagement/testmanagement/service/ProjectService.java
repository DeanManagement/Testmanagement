package com.deanmanagement.testmanagement.service;

import com.deanmanagement.testmanagement.dto.CreateProjectRequest;
import com.deanmanagement.testmanagement.dto.ProjectMapper;
import com.deanmanagement.testmanagement.dto.ProjectResponse;
import com.deanmanagement.testmanagement.dto.UpdateProjectRequest;
import com.deanmanagement.testmanagement.entity.Project;
import com.deanmanagement.testmanagement.exception.ResourceNotFoundException;
import com.deanmanagement.testmanagement.repository.ProjectRepository;
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

    public List<ProjectResponse> findAll() {
        return projectRepository.findAll().stream()
                .map(projectMapper::toResponse)
                .toList();
    }

    public ProjectResponse findById(UUID id) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project", id));
        return projectMapper.toResponse(project);
    }

    @Transactional
    public ProjectResponse create(CreateProjectRequest request) {
        Project project = projectMapper.toEntity(request);
        project.setKey(generateKey(request.name()));
        project = projectRepository.save(project);
        return projectMapper.toResponse(project);
    }

    @Transactional
    public ProjectResponse update(UUID id, UpdateProjectRequest request) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project", id));
        projectMapper.updateEntity(request, project);
        project = projectRepository.save(project);
        return projectMapper.toResponse(project);
    }

    @Transactional
    public void delete(UUID id) {
        if (!projectRepository.existsById(id)) {
            throw new ResourceNotFoundException("Project", id);
        }
        projectRepository.deleteById(id);
    }

    public List<ProjectResponse> searchByKey(String key) {
        return projectRepository.findByKeyContainingIgnoreCase(key).stream()
                .map(projectMapper::toResponse)
                .toList();
    }

    String generateKey(String name) {
        String[] words = name.trim().split("\\s+");
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
