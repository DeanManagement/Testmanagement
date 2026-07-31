package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Allocates per-project sequential numbers for test-case and test-run keys. Uses a pessimistic row
 * lock so concurrent creates don't collide, and is portable across PostgreSQL and the H2 dev/test DB
 * (replacing a Postgres-only {@code UPDATE ... RETURNING}).
 */
@Service
@RequiredArgsConstructor
public class ProjectSequenceService {

    private final ProjectRepository projectRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public int nextTestCaseNumber(UUID projectId) {
        Project project = lock(projectId);
        int number = project.getNextTestCaseNumber();
        project.setNextTestCaseNumber(number + 1);
        return number;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public int nextTestRunNumber(UUID projectId) {
        Project project = lock(projectId);
        int number = project.getNextTestRunNumber();
        project.setNextTestRunNumber(number + 1);
        return number;
    }

    private Project lock(UUID projectId) {
        return projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
    }
}
