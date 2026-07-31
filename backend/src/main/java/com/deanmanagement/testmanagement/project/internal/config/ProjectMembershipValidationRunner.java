package com.deanmanagement.testmanagement.project.internal.config;

import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectMemberRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * One-off startup check supporting PRD-001 rollout. With project-role enforcement now active, a
 * project with no {@link ProjectRole#ADMIN} member is manageable only by system admins. This runner
 * logs any such projects so an operator can assign an owner. It is non-destructive (log only).
 */
@Component
@RequiredArgsConstructor
public class ProjectMembershipValidationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProjectMembershipValidationRunner.class);

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;

    @Override
    public void run(ApplicationArguments args) {
        projectRepository.findAll().forEach(project -> {
            boolean hasAdmin = projectMemberRepository.findByProjectId(project.getId()).stream()
                    .anyMatch(member -> member.getRole() == ProjectRole.ADMIN);
            if (!hasAdmin) {
                log.warn("Project '{}' ({}) has no ADMIN member; it is only manageable by system admins. "
                                + "Assign a project admin via the members API.",
                        project.getName(), project.getId());
            }
        });
    }
}
