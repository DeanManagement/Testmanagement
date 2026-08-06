package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.TestRun;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestRunRepository;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the path variables of the external (CI) API, which accept either the human-readable key
 * or the UUID. CI configs are written by hand and both forms are visible in the UI and in API
 * responses, so rejecting one of them is a papercut that surfaces as a confusing 404.
 *
 * <p>Keys and UUIDs cannot collide: a project key is at most 10 characters and a run key carries a
 * {@code -Run-} infix, while a UUID is a fixed 36-character form.
 */
@Service
@RequiredArgsConstructor
public class ExternalRefResolver {

    private final ProjectRepository projectRepository;
    private final TestRunRepository testRunRepository;

    /** @param ref a project key ({@code TES}) or a project UUID. */
    public Project resolveProject(String ref) {
        return asUuid(ref)
                .flatMap(projectRepository::findById)
                .or(() -> projectRepository.findByKey(ref))
                .orElseThrow(() -> new ResourceNotFoundException("Project", ref));
    }

    /** @param ref a test run key ({@code TES-Run-1}) or a test run UUID. */
    public TestRun resolveTestRun(String ref) {
        return asUuid(ref)
                .flatMap(testRunRepository::findById)
                .or(() -> testRunRepository.findByKey(ref))
                .orElseThrow(() -> new ResourceNotFoundException("TestRun", ref));
    }

    public static Optional<UUID> asUuid(String ref) {
        if (ref == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(ref));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
