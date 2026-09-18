package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.dto.apiKey.CreateApiKeyRequest;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.repository.ApiKeyRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.service.ApiKeyService;
import com.deanmanagement.testmanagement.project.internal.service.ProjectService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Harness for tests that call the MCP tools as an authenticated API key would — the security
 * context is set to the key's service user, exactly as {@code ApiKeyAuthenticationFilter} leaves
 * it. Not {@code @Transactional}, for the reason {@link McpToolSurfaceApiTest} gives: data is torn
 * down explicitly and each test gets its own project keys.
 */
@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = "app.mcp.enabled=true")
abstract class McpToolApiTestSupport {

    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private ApiKeyRepository apiKeyRepository;
    @Autowired
    private ApiKeyService apiKeyService;
    @Autowired
    private ProjectService projectService;
    @Autowired
    private McpWriteThrottle writeThrottle;
    @Autowired
    protected TestCaseTools testCaseTools;
    @Autowired
    protected TestRunWriteTools testRunWriteTools;
    @Autowired
    protected TestRunReadTools testRunReadTools;
    @Autowired
    protected TestCaseBulkTools testCaseBulkTools;
    @Autowired
    protected TestResultRecordingTools resultRecordingTools;
    @Autowired
    protected TestStepRecordingTools stepRecordingTools;

    protected Project project;
    protected Project otherProject;

    @BeforeEach
    void setUpProjects() {
        writeThrottle.reset();
        String suffix = Integer.toHexString(new java.util.Random().nextInt(0xFFFFF));
        project = newProject("MCP Project", "C" + suffix);
        otherProject = newProject("Other Project", "D" + suffix);
    }

    @AfterEach
    void tearDownProjects() {
        SecurityContextHolder.clearContext();
        // Through the service: it removes the project's executions first, which a cascade cannot.
        projectService.delete(project.getId(), null);
        projectService.delete(otherProject.getId(), null);
    }

    private Project newProject(String name, String key) {
        Project p = new Project();
        p.setName(name);
        p.setKey(key);
        return projectRepository.save(p);
    }

    /**
     * Authenticates as a fresh key's service user, replacing whoever was authenticated before.
     *
     * @return the service user's id
     */
    protected UUID authenticateAs(Project target, ProjectRole role) {
        var created = apiKeyService.create(new CreateApiKeyRequest(
                "agent-" + role + "-" + UUID.randomUUID(), target.getId(), role));
        UUID serviceUserId = apiKeyRepository.findById(created.id()).orElseThrow()
                .getServiceUser().getId();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(serviceUserId.toString(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_API_KEY"),
                                new SimpleGrantedAuthority("ROLE_USER"))));
        return serviceUserId;
    }

    protected McpDtos.CreatedTestCase createCase(String title, String... stepActions) {
        List<McpDtos.Step> steps = Arrays.stream(stepActions)
                .map(action -> new McpDtos.Step(action, null, null))
                .toList();
        return testCaseTools.createTestCase(title, Priority.MEDIUM, null, null, null, null,
                steps, null, null, null);
    }

    protected McpDtos.CreatedTestRun runOf(McpDtos.CreatedTestCase... cases) {
        Set<UUID> ids = Arrays.stream(cases).map(McpDtos.CreatedTestCase::id)
                .collect(Collectors.toSet());
        return testRunWriteTools.createTestRun("Run", null, ids, null, null);
    }
}
