package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.entity.ProjectMember;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectMemberRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * What the signed-in user is actually able to do, so the interface can stop describing things they
 * cannot reach — currently the user manual, which shows a viewer no instructions for writing test
 * cases and a non-admin no chapter on SSO.
 *
 * <p>System-admin status already travels in the JWT, so the part worth an endpoint is the project
 * roles: they live in {@code project_members} and a user can hold different ones in different
 * projects. The manual is instance-wide rather than scoped to a project, so it asks whether the
 * reader holds a role <em>anywhere</em> — someone who is a tester on one project needs the
 * authoring chapter even if they are only a viewer on three others.
 */
@RestController
@RequestMapping("/api/me")
@Tag(name = "Me", description = "What the signed-in user can do")
@RequiredArgsConstructor
public class MyCapabilitiesController {

    private final ProjectMemberRepository projectMemberRepository;

    /**
     * @param projectRoles      every distinct role held, strongest first
     * @param highestRole       the strongest, or null when the user belongs to no project yet
     * @param projectMemberships how many projects they are in, so the UI can tell "no access yet"
     *                          from "viewer everywhere"
     */
    public record MyCapabilitiesResponse(boolean systemAdmin, List<ProjectRole> projectRoles,
                                         ProjectRole highestRole, int projectMemberships) {}

    @GetMapping("/capabilities")
    public MyCapabilitiesResponse capabilities(Authentication authentication) {
        boolean systemAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));

        List<ProjectMember> memberships =
                projectMemberRepository.findByUserId(UUID.fromString(authentication.getName()));

        // ProjectRole ranks ADMIN(0) → VIEWER(2), so natural enum order is already strongest first.
        Set<ProjectRole> roles = new TreeSet<>(Comparator.comparingInt(Enum::ordinal));
        memberships.forEach(member -> roles.add(member.getRole()));

        // A system admin passes every project check, so the manual should treat them as able to do
        // anything a project admin can — otherwise the chapters they most need would be hidden.
        if (systemAdmin) {
            roles.add(ProjectRole.ADMIN);
        }

        return new MyCapabilitiesResponse(systemAdmin, List.copyOf(roles),
                roles.isEmpty() ? null : roles.iterator().next(), memberships.size());
    }
}
