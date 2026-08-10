package com.deanmanagement.testmanagement.project.internal.access;

import com.deanmanagement.testmanagement.project.internal.entity.ProjectMember;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectMemberRepository;
import com.deanmanagement.testmanagement.shared.exception.ForbiddenException;
import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectAccessServiceTest {

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private ProjectAccessService access;

    private static final UUID USER = UUID.randomUUID();
    private static final UUID PROJECT = UUID.randomUUID();

    private ProjectMember memberWith(ProjectRole role) {
        ProjectMember m = new ProjectMember();
        m.setRole(role);
        return m;
    }

    private void mockNonAdminUser() {
        User u = new User();
        u.setSystemAdmin(false);
        lenient().when(userService.findEntityById(USER)).thenReturn(Optional.of(u));
    }

    @Test
    void requireRole_memberWithSufficientRole_passes() {
        mockNonAdminUser();
        when(projectMemberRepository.findByUserIdAndProjectId(USER, PROJECT))
                .thenReturn(Optional.of(memberWith(ProjectRole.ADMIN)));

        assertThatCode(() -> access.requireRole(USER, PROJECT, ProjectRole.TESTER))
                .doesNotThrowAnyException();
    }

    @Test
    void requireRole_memberWithInsufficientRole_throws() {
        mockNonAdminUser();
        when(projectMemberRepository.findByUserIdAndProjectId(USER, PROJECT))
                .thenReturn(Optional.of(memberWith(ProjectRole.VIEWER)));

        assertThatThrownBy(() -> access.requireRole(USER, PROJECT, ProjectRole.TESTER))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void requireRole_nonMember_throws() {
        mockNonAdminUser();
        when(projectMemberRepository.findByUserIdAndProjectId(USER, PROJECT))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> access.requireRole(USER, PROJECT, ProjectRole.VIEWER))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void requireRole_systemAdmin_bypassesMembership() {
        User admin = new User();
        admin.setSystemAdmin(true);
        when(userService.findEntityById(USER)).thenReturn(Optional.of(admin));

        assertThatCode(() -> access.requireRole(USER, PROJECT, ProjectRole.ADMIN))
                .doesNotThrowAnyException();
    }

    @Test
    void requireMember_systemAdmin_returnsAdminRole() {
        User admin = new User();
        admin.setSystemAdmin(true);
        when(userService.findEntityById(USER)).thenReturn(Optional.of(admin));

        assertThat(access.requireMember(USER, PROJECT)).isEqualTo(ProjectRole.ADMIN);
    }

    @Test
    void requireMember_member_returnsTheirRole() {
        mockNonAdminUser();
        when(projectMemberRepository.findByUserIdAndProjectId(USER, PROJECT))
                .thenReturn(Optional.of(memberWith(ProjectRole.TESTER)));

        assertThat(access.requireMember(USER, PROJECT)).isEqualTo(ProjectRole.TESTER);
    }

    @Test
    void requireMember_nonMember_throws() {
        mockNonAdminUser();
        when(projectMemberRepository.findByUserIdAndProjectId(USER, PROJECT))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> access.requireMember(USER, PROJECT))
                .isInstanceOf(ForbiddenException.class);
    }

    // --- PRD-025 §3.2: principal resolution -------------------------------------------------

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolvedCallerOrNull_noAuthentication_returnsNull() {
        // Permit-all/dev context: the filter chain owns authentication, so the role check is
        // skipped rather than refused. This is the parity the old fail-open was protecting.
        assertThat(access.resolvedCallerOrNull()).isNull();
    }

    @Test
    void resolvedCallerOrNull_anonymous_returnsNull() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        assertThat(access.resolvedCallerOrNull()).isNull();
    }

    @Test
    void resolvedCallerOrNull_uuidPrincipal_resolves() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USER.toString(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        assertThat(access.resolvedCallerOrNull()).isEqualTo(USER);
    }

    /**
     * The regression that matters. An API-key request used to arrive with the principal
     * {@code "api-key:<name>"}; because that does not parse as a UUID, the old
     * {@code currentUserId()} returned null and both {@code ProjectRoleAspect} and
     * {@code requireRoleForCurrentUser} treated it as "nobody is logged in" and waved it through.
     * Any principal that exists but cannot be resolved is now refused.
     */
    @Test
    void resolvedCallerOrNull_unresolvablePrincipal_throwsInsteadOfFailingOpen() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("api-key:CI Pipeline", null,
                        List.of(new SimpleGrantedAuthority("ROLE_API_KEY"))));

        assertThatThrownBy(() -> access.resolvedCallerOrNull())
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("does not resolve to a user");
    }

    @Test
    void requireRoleForCurrentUser_unresolvablePrincipal_throws() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("api-key:CI Pipeline", null,
                        List.of(new SimpleGrantedAuthority("ROLE_API_KEY"))));

        assertThatThrownBy(() -> access.requireRoleForCurrentUser(PROJECT, ProjectRole.TESTER))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void requireRoleForCurrentUser_noPrincipal_isSkipped() {
        assertThatCode(() -> access.requireRoleForCurrentUser(PROJECT, ProjectRole.TESTER))
                .doesNotThrowAnyException();
    }
}
