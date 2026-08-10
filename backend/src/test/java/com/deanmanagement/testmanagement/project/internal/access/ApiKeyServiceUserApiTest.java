package com.deanmanagement.testmanagement.project.internal.access;

import com.deanmanagement.testmanagement.project.internal.dto.apiKey.ApiKeyCreatedResponse;
import com.deanmanagement.testmanagement.project.internal.dto.apiKey.CreateApiKeyRequest;
import com.deanmanagement.testmanagement.project.internal.entity.ApiKey;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectMember;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.repository.ApiKeyRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectMemberRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.service.ApiKeyService;
import com.deanmanagement.testmanagement.project.internal.service.ProjectMemberService;
import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PRD-025 §3.2. An API key now authenticates as a real service user holding a real project role,
 * which is what makes {@code @RequireProjectRole} apply to it at all.
 *
 * <p>Before this, the API-key principal was the string {@code "api-key:<name>"}. That does not
 * parse as a UUID, so {@code ProjectAccessService.currentUserId()} returned {@code null} and
 * {@code ProjectRoleAspect} skipped the check — the reason external endpoints had to live behind
 * their own path-based scope check rather than reusing the domain authorization.
 */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Transactional
class ApiKeyServiceUserApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private ProjectMemberRepository projectMemberRepository;
    @Autowired
    private ApiKeyRepository apiKeyRepository;
    @Autowired
    private ApiKeyService apiKeyService;
    @Autowired
    private ProjectMemberService projectMemberService;
    @Autowired
    private UserService userService;

    private Project project;
    private Project otherProject;

    @BeforeEach
    void setUp() {
        project = new Project();
        project.setName("Keyed Project");
        project.setKey("KEYED");
        project = projectRepository.save(project);

        otherProject = new Project();
        otherProject.setName("Other Project");
        otherProject.setKey("OTHER");
        otherProject = projectRepository.save(otherProject);
    }

    private ApiKeyCreatedResponse createKey(String name, ProjectRole role) {
        return apiKeyService.create(new CreateApiKeyRequest(name, project.getId(), role));
    }

    private User serviceUserOf(UUID apiKeyId) {
        ApiKey key = apiKeyRepository.findById(apiKeyId).orElseThrow();
        return key.getServiceUser();
    }

    @Test
    void creatingAKeyCreatesAServiceUserWithTheRequestedRole() {
        ApiKeyCreatedResponse created = createKey("agent", ProjectRole.VIEWER);

        User serviceUser = serviceUserOf(created.id());
        assertThat(serviceUser).isNotNull();
        assertThat(serviceUser.isServiceAccount()).isTrue();
        assertThat(serviceUser.getPasswordHash()).isNull();
        assertThat(serviceUser.isSystemAdmin()).isFalse();
        assertThat(serviceUser.getEmail()).endsWith("@service.invalid");

        Optional<ProjectMember> membership = projectMemberRepository
                .findByUserIdAndProjectId(serviceUser.getId(), project.getId());
        assertThat(membership).isPresent();
        assertThat(membership.get().getRole()).isEqualTo(ProjectRole.VIEWER);
    }

    /**
     * The heart of it: {@code currentUserId()} must now resolve for an API-key request, and the
     * role check must actually run against the key's membership.
     */
    @Test
    void theServiceUsersRoleIsEnforcedByProjectAccessService() {
        ApiKeyCreatedResponse viewerKey = createKey("viewer-agent", ProjectRole.VIEWER);
        ApiKeyCreatedResponse testerKey = createKey("tester-agent", ProjectRole.TESTER);
        ProjectAccessService access = accessService();

        UUID viewerUser = serviceUserOf(viewerKey.id()).getId();
        UUID testerUser = serviceUserOf(testerKey.id()).getId();

        assertThat(access.requireMember(viewerUser, project.getId())).isEqualTo(ProjectRole.VIEWER);
        assertThat(access.requireMember(testerUser, project.getId())).isEqualTo(ProjectRole.TESTER);

        assertThat(catchThrowableOf(() -> access.requireRole(viewerUser, project.getId(), ProjectRole.TESTER)))
                .as("a VIEWER key must not pass a TESTER gate")
                .isNotNull();
        assertThat(catchThrowableOf(() -> access.requireRole(testerUser, project.getId(), ProjectRole.TESTER)))
                .as("a TESTER key must pass a TESTER gate")
                .isNull();
        assertThat(catchThrowableOf(() -> access.requireRole(testerUser, otherProject.getId(), ProjectRole.VIEWER)))
                .as("a key must not reach another project")
                .isNotNull();
    }

    @Test
    void revokingAKeyDropsItsMembership() {
        ApiKeyCreatedResponse created = createKey("short-lived", ProjectRole.TESTER);
        UUID serviceUserId = serviceUserOf(created.id()).getId();
        assertThat(projectMemberRepository.findByUserIdAndProjectId(serviceUserId, project.getId()))
                .isPresent();

        apiKeyService.revoke(created.id());

        assertThat(projectMemberRepository.findByUserIdAndProjectId(serviceUserId, project.getId()))
                .as("membership is dropped, so a racing request is refused by authorization too")
                .isEmpty();
        // The user survives, so anything it authored stays attributable.
        assertThat(userService.findEntityById(serviceUserId)).isPresent();
    }

    @Test
    void serviceAccountsAreHiddenFromUserAndMemberListings() {
        ApiKeyCreatedResponse created = createKey("hidden", ProjectRole.TESTER);
        UUID serviceUserId = serviceUserOf(created.id()).getId();

        assertThat(userService.findAll())
                .as("would otherwise appear in user administration")
                .noneMatch(user -> user.id().equals(serviceUserId));
        assertThat(projectMemberService.findByProject(project.getId()))
                .as("would otherwise appear in assignee pickers")
                .noneMatch(member -> member.userId().equals(serviceUserId));
    }

    @Test
    void serviceAccountsCannotLogIn() throws Exception {
        ApiKeyCreatedResponse created = createKey("no-login", ProjectRole.TESTER);
        String email = serviceUserOf(created.id()).getEmail();

        // A real password value, so this exercises the credential path rather than bean validation.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"anything\"}"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * The role has to bite on the surface a key can actually reach, or "Viewer (read only)" in the
     * create dialog is a promise the backend does not keep.
     */
    private static final String SUREFIRE_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="com.example.CalcTest" tests="1">
              <testcase classname="com.example.CalcTest" name="adds"/>
            </testsuite>
            """;

    @Test
    void aViewerKeyCannotIngestResults() throws Exception {
        String viewerKey = createKey("viewer-agent", ProjectRole.VIEWER).rawKey();

        mockMvc.perform(post("/api/external/projects/{k}/test-runs/junit", "KEYED")
                        .header("X-API-Key", viewerKey)
                        .contentType(MediaType.APPLICATION_XML).content(SUREFIRE_XML))
                .andExpect(status().isForbidden());
    }

    @Test
    void aTesterKeyCanIngestResults() throws Exception {
        String testerKey = createKey("tester-agent", ProjectRole.TESTER).rawKey();

        mockMvc.perform(post("/api/external/projects/{k}/test-runs/junit", "KEYED")
                        .header("X-API-Key", testerKey)
                        .contentType(MediaType.APPLICATION_XML).content(SUREFIRE_XML))
                .andExpect(status().isCreated());
    }

    @Test
    void adminRoleIsRefusedForKeys() {
        assertThat(catchThrowableOf(() -> createKey("too-powerful", ProjectRole.ADMIN)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ProjectAccessService accessService() {
        return new ProjectAccessService(projectMemberRepository, userService);
    }

    private static Throwable catchThrowableOf(Runnable runnable) {
        try {
            runnable.run();
            return null;
        } catch (Throwable t) {
            return t;
        }
    }
}
