package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectMember;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestResult;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestRun;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectMemberRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestResultRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestRunRepository;
import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.internal.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The flaky analytics endpoint (PRD-016). Analytics are still project data, so the assertions that
 * matter are membership scoping and who may trigger the label sync.
 */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Transactional
class FlakyAnalyticsApiTest {

    private static final Instant BASE = Instant.parse("2026-01-01T09:00:00Z");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private ProjectMemberRepository projectMemberRepository;
    @Autowired
    private TestCaseRepository testCaseRepository;
    @Autowired
    private TestRunRepository testRunRepository;
    @Autowired
    private TestResultRepository testResultRepository;

    private final AtomicInteger sequence = new AtomicInteger();

    private String admin;
    private String viewer;
    private String outsider;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        User adminUser = saveUser();
        User viewerUser = saveUser();
        admin = adminUser.getId().toString();
        viewer = viewerUser.getId().toString();
        outsider = saveUser().getId().toString();

        Project project = new Project();
        project.setName("Analytics");
        project.setKey("ANL" + sequence.incrementAndGet());
        project = projectRepository.save(project);
        projectId = project.getId();

        saveMember(adminUser, project, ProjectRole.ADMIN);
        saveMember(viewerUser, project, ProjectRole.VIEWER);

        TestCase flaky = new TestCase();
        flaky.setProject(project);
        flaky.setKey("ANL-1");
        flaky.setTitle("Intermittent checkout");
        flaky.setPriority(Priority.HIGH);
        flaky.setStatus(TestCaseStatus.ACTIVE);
        flaky = testCaseRepository.save(flaky);

        String outcomes = "PFPFPFPF";
        for (int i = 0; i < outcomes.length(); i++) {
            TestRun run = new TestRun();
            run.setProject(project);
            run.setKey("ANL-R" + sequence.incrementAndGet());
            run.setName("run");
            run.setStatus(TestRunStatus.COMPLETED);
            run.setEndTime(BASE.plus(i, ChronoUnit.HOURS));
            run = testRunRepository.save(run);

            TestResult result = new TestResult();
            result.setTestRun(run);
            result.setTestCase(flaky);
            result.setStatus(outcomes.charAt(i) == 'P' ? TestResultStatus.PASSED : TestResultStatus.FAILED);
            testResultRepository.save(result);
        }
    }

    private User saveUser() {
        User u = new User();
        u.setEmail("u-" + UUID.randomUUID() + "@test.local");
        u.setDisplayName("u");
        u.setPasswordHash("x");
        u.setSystemAdmin(false);
        return userRepository.save(u);
    }

    private void saveMember(User user, Project project, ProjectRole role) {
        ProjectMember pm = new ProjectMember();
        pm.setUser(user);
        pm.setProject(project);
        pm.setRole(role);
        projectMemberRepository.save(pm);
    }

    private String url() {
        return "/api/projects/" + projectId + "/analytics/flaky";
    }

    @Test
    void viewerCanReadFlakyTests() throws Exception {
        mockMvc.perform(get(url()).with(user(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].testCaseKey").value("ANL-1"))
                .andExpect(jsonPath("$[0].flakyScore").value(1.0))
                .andExpect(jsonPath("$[0].runsConsidered").value(8))
                .andExpect(jsonPath("$[0].flaky").value(true));
    }

    @Test
    void responseCarriesEnoughContextToLinkToTheCase() throws Exception {
        mockMvc.perform(get(url()).with(user(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].testCaseId").isNotEmpty())
                .andExpect(jsonPath("$[0].title").value("Intermittent checkout"))
                .andExpect(jsonPath("$[0].failRate").value(0.5));
    }

    @Test
    void nonMemberCannotReadAnotherProjectsAnalytics() throws Exception {
        mockMvc.perform(get(url()).with(user(outsider)))
                .andExpect(status().isForbidden());
    }

    @Test
    void limitIsCappedRatherThanTrusted() throws Exception {
        // An unbounded limit would let a member ask for every case in one query.
        mockMvc.perform(get(url()).param("limit", "10000").with(user(viewer)))
                .andExpect(status().isOk());
    }

    @Test
    void viewerCannotTriggerTheLabelSync() throws Exception {
        // It edits test cases and writes audit entries, so it is an admin action.
        mockMvc.perform(post(url() + "/sync-labels").with(user(viewer)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanTriggerTheLabelSyncAndItIsANoOpByDefault() throws Exception {
        mockMvc.perform(post(url() + "/sync-labels").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(0));
    }
}
