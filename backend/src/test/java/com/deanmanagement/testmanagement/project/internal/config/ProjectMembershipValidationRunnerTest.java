package com.deanmanagement.testmanagement.project.internal.config;

import com.deanmanagement.testmanagement.project.internal.repository.ProjectMemberRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.dao.InvalidDataAccessApiUsageException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

/**
 * The runner is an advisory, log-only check. A row it cannot read (e.g. a role value no longer in
 * {@code ProjectRole}) used to abort application startup entirely.
 */
@ExtendWith(MockitoExtension.class)
class ProjectMembershipValidationRunnerTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @InjectMocks
    private ProjectMembershipValidationRunner runner;

    @Test
    void run_doesNotFailStartup_whenMembershipDataCannotBeRead() {
        when(projectRepository.findAll()).thenThrow(new InvalidDataAccessApiUsageException(
                "No enum constant ProjectRole.Ask Plan"));

        assertThatCode(() -> runner.run(new DefaultApplicationArguments()))
                .doesNotThrowAnyException();
    }
}
