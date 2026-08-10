package com.deanmanagement.testmanagement.project.internal.access;

import com.deanmanagement.testmanagement.project.internal.entity.ProjectMember;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectMemberRepository;
import com.deanmanagement.testmanagement.shared.exception.ForbiddenException;
import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Central project authorization. Confirms a caller is a member of a project and holds at least a
 * given role, with a global system-admin bypass. Used by {@link ProjectRoleAspect} for the common
 * path-variable case and directly by services as a defense-in-depth backstop where the project id
 * is not in the request path (screenshots, step images).
 */
@Service
@RequiredArgsConstructor
public class ProjectAccessService {

    private final ProjectMemberRepository projectMemberRepository;
    private final UserService userService;

    /**
     * Confirms the caller is a member of the project (or a system admin) and returns their effective
     * role. System admins get {@link ProjectRole#ADMIN}.
     *
     * @throws ForbiddenException if the caller is neither a member nor a system admin.
     */
    public ProjectRole requireMember(UUID userId, UUID projectId) {
        if (isSystemAdmin(userId)) {
            return ProjectRole.ADMIN;
        }
        return projectMemberRepository.findByUserIdAndProjectId(userId, projectId)
                .map(ProjectMember::getRole)
                .orElseThrow(() -> new ForbiddenException("Not a member of this project"));
    }

    /**
     * Confirms the caller holds at least {@code minRole} on the project. System admins always pass.
     *
     * @throws ForbiddenException if the caller is not a member, or holds an insufficient role.
     */
    public void requireRole(UUID userId, UUID projectId, ProjectRole minRole) {
        if (isSystemAdmin(userId)) {
            return;
        }
        ProjectRole role = projectMemberRepository.findByUserIdAndProjectId(userId, projectId)
                .map(ProjectMember::getRole)
                .orElseThrow(() -> new ForbiddenException("Not a member of this project"));
        if (!role.satisfies(minRole)) {
            throw new ForbiddenException("Requires " + minRole + " role on this project");
        }
    }

    /**
     * Convenience overload that resolves the caller from the current {@link SecurityContextHolder}.
     * Used by service-layer backstops. If there is no authenticated principal (e.g. an internal
     * call outside a request), the check is skipped.
     */
    public void requireRoleForCurrentUser(UUID projectId, ProjectRole minRole) {
        UUID userId = resolvedCallerOrNull();
        if (userId == null) {
            return;
        }
        requireRole(userId, projectId, minRole);
    }

    /**
     * The caller for authorization purposes.
     *
     * <p>Returns {@code null} only when the request carries no principal at all — an anonymous or
     * permit-all context, where the filter chain is the thing responsible for authentication and
     * skipping the role check preserves dev/test parity.
     *
     * <p>When there <em>is</em> an authenticated principal but it does not resolve to a user id,
     * this throws. That case used to fall into the same "skip the check" branch, which is how
     * API-key callers — whose principal was the string {@code "api-key:<name>"} — bypassed project
     * role enforcement entirely (PRD-025 §3.2). Every principal that reaches here now either names
     * a user or is refused.
     *
     * @throws ForbiddenException if an authenticated principal cannot be resolved to a user
     */
    public UUID resolvedCallerOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null
                || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        try {
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException e) {
            throw new ForbiddenException("Authenticated principal does not resolve to a user");
        }
    }

    public boolean isSystemAdmin(UUID userId) {
        if (userId == null) {
            return false;
        }
        return userService.findEntityById(userId)
                .map(User::isSystemAdmin)
                .orElse(false);
    }

}
