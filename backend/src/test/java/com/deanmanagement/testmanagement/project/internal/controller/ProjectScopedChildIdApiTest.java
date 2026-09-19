package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.service.ProjectEnvironmentService;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectMember;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestPlan;
import com.deanmanagement.testmanagement.project.internal.entity.TestPlanStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestResult;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestRun;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import com.deanmanagement.testmanagement.project.internal.repository.BugReportRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectMemberRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestPlanRepository;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PRD-027 §3.5 — caller-supplied child ids must not cross a project boundary.
 *
 * <p>PRD-025 §8 fixed one instance of this in {@code TestSuiteService.resolveTestCases} and closed
 * with an instruction to grep for the pattern. Nobody did. {@code TestRunService},
 * {@code BugReportService} and {@code TestPlanService} held six more between them, every one of
 * them reachable through the REST API and not only through the MCP tools that prompted the audit.
 *
 * <p>These assertions are written against **the REST endpoints**, deliberately. The bugs did not
 * originate with the agent surface and they are not fixed by guarding it — a service-level unit
 * test with a mocked repository would have passed happily against the broken code, because the
 * mock returns whatever the test tells it to.
 *
 * <p>The caller here is a system admin, which passes {@code @RequireProjectRole} for every project.
 * That is the point: authorization to act <em>on a project</em> is not authorization to pull
 * another project's rows into it, and the check that stops it has to live in the service.
 */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Transactional
class ProjectScopedChildIdApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private TestCaseRepository testCaseRepository;
    @Autowired private TestPlanRepository testPlanRepository;
    @Autowired private TestRunRepository testRunRepository;
    @Autowired private TestResultRepository testResultRepository;
    @Autowired private BugReportRepository bugReportRepository;
    @Autowired private ProjectEnvironmentService environmentService;

    private String admin;

    /** "Ours" — the project every request below is addressed to. */
    private Project ours;
    private Project theirs;
    private UUID ourCaseId;
    private UUID ourPlanId;
    private UUID ourRunId;
    private UUID ourResultId;
    private UUID ourMemberId;

    /** "Theirs" — a second project the caller also administers, but must not be able to borrow from. */
    private UUID theirCaseId;
    private UUID theirPlanId;
    private UUID theirRunId;
    private UUID theirResultId;

    /** A real user who is a member of neither project. */
    private UUID outsiderId;

    /** A plain (non-system) admin of "ours" only — the caller for the member-management tests. */
    private UUID ourAdminId;

    /** A VIEWER membership row belonging to "theirs", which "ours" must not be able to touch. */
    private UUID theirMembershipId;

    @BeforeEach
    void setUp() {
        admin = newUser("admin", true).getId().toString();
        outsiderId = newUser("outsider", false).getId();

        ours = newProject("Ours", "OURS", true);
        theirs = newProject("Theirs", "THEIRS", true);

        User member = newUser("member", false);
        ourMemberId = member.getId();
        newMembership(member, ours, ProjectRole.TESTER);

        User ourAdmin = newUser("ourAdmin", false);
        ourAdminId = ourAdmin.getId();
        newMembership(ourAdmin, ours, ProjectRole.ADMIN);

        theirMembershipId = newMembership(newUser("theirMember", false), theirs, ProjectRole.VIEWER);

        ourCaseId = newCase(ours, "Our case", "OURS-1").getId();
        ourPlanId = newPlan(ours, "Our plan").getId();
        TestRun ourRun = newRun(ours, "OURS-Run-1");
        ourRunId = ourRun.getId();
        ourResultId = newResult(ourRun, newCase(ours, "Our other case", "OURS-2")).getId();

        theirCaseId = newCase(theirs, "Their case", "THEIRS-1").getId();
        theirPlanId = newPlan(theirs, "Their plan").getId();
        TestRun theirRun = newRun(theirs, "THEIRS-Run-1");
        theirRunId = theirRun.getId();
        theirResultId = newResult(theirRun, newCase(theirs, "Their other case", "THEIRS-2")).getId();
    }

    // --- test runs -----------------------------------------------------------------------------

    @Test
    void createRun_withAnotherProjectsTestCase_isNotFound() throws Exception {
        createRun("{\"name\":\"R\",\"testCaseIds\":[\"" + theirCaseId + "\"]}")
                .andExpect(status().isNotFound());

        assertThat(testRunRepository.countByProjectId(ours.getId()))
                .as("no run may be created").isEqualTo(1);
    }

    /**
     * The half-success that made this worth fixing rather than merely tidying. {@code findAllById}
     * dropped ids it could not resolve, so a run seeded with a good id and a bad one was created
     * holding only the good one — and reported success. An agent asked to run ten cases got a
     * seven-case run back and no way to tell it apart from a ten-case run that passed.
     */
    @Test
    void createRun_withOneUnknownId_createsNothingAndNamesTheOffender() throws Exception {
        UUID unknown = UUID.randomUUID();

        createRun("{\"name\":\"R\",\"testCaseIds\":[\"" + ourCaseId + "\",\"" + unknown + "\"]}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(unknown.toString())));

        assertThat(testRunRepository.countByProjectId(ours.getId()))
                .as("partial seeding is not a success").isEqualTo(1);
    }

    /**
     * The one cross-project <em>write</em>. A run attached to another project's plan is counted by
     * that plan's summary, so this moved a pass rate somebody else reports on.
     */
    @Test
    void createRun_withAnotherProjectsTestPlan_isNotFound() throws Exception {
        createRun("{\"name\":\"R\",\"testPlanId\":\"" + theirPlanId + "\"}")
                .andExpect(status().isNotFound());
    }

    /** PRD-032: an environment id is scoped like every other child id. */
    @Test
    void createRun_withAnotherProjectsEnvironment_isNotFound() throws Exception {
        UUID theirEnvironmentId = environmentService.resolve(theirs.getId(), null, "staging").getId();

        createRun("{\"name\":\"R\",\"environmentId\":\"" + theirEnvironmentId + "\"}")
                .andExpect(status().isNotFound());
    }

    @Test
    void createRun_withOwnEnvironmentId_takesItsName() throws Exception {
        UUID ourEnvironmentId = environmentService.resolve(ours.getId(), null, "Staging").getId();

        createRun("{\"name\":\"R\",\"environmentId\":\"" + ourEnvironmentId + "\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.environment").value("Staging"));
    }

    @Test
    void createRun_withNonMemberExecutor_isNotFound() throws Exception {
        createRun("{\"name\":\"R\",\"executorId\":\"" + outsiderId + "\"}")
                .andExpect(status().isNotFound());
    }

    @Test
    void createRun_withOwnProjectIds_stillWorks() throws Exception {
        createRun("{\"name\":\"R\",\"testCaseIds\":[\"" + ourCaseId + "\"],"
                + "\"testPlanId\":\"" + ourPlanId + "\","
                + "\"executorId\":\"" + ourMemberId + "\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.results.length()").value(1));
    }

    /**
     * The same cross-project write as {@code createRun_withAnotherProjectsTestPlan}, through PUT.
     * Found only because restoring the fixed files after proving the tests red made the remaining
     * unscoped call sites visible in a grep — the create path had been fixed and this one, four
     * hundred lines away in the same class, had not.
     */
    @Test
    void updateRun_movingItToAnotherProjectsTestPlan_isNotFound() throws Exception {
        mockMvc.perform(put("/api/projects/{p}/test-runs/{r}", ours.getId(), ourRunId)
                        .with(user(admin)).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"R\",\"testPlanId\":\"" + theirPlanId + "\"}"))
                .andExpect(status().isNotFound());
    }

    /**
     * {@code addResult} is the path PRD-027's {@code record_test_result} falls back to for a case
     * that is not in the run, so an unscoped lookup here would have been reachable by an agent
     * passing any UUID it had ever seen.
     */
    @Test
    void addResult_forAnotherProjectsTestCase_isNotFound() throws Exception {
        mockMvc.perform(post("/api/projects/{p}/test-runs/{r}/results", ours.getId(), ourRunId)
                        .with(user(admin)).with(csrf())
                        .contentType("application/json")
                        .content("{\"testCaseId\":\"" + theirCaseId + "\",\"status\":\"FAILED\"}"))
                .andExpect(status().isNotFound());
    }

    // --- bug reports ---------------------------------------------------------------------------

    @Test
    void createBug_withAnotherProjectsTestResult_isNotFound() throws Exception {
        createBug("\"testResultId\":\"" + theirResultId + "\"")
                .andExpect(status().isNotFound());

        assertThat(bugReportRepository.findByProjectIdWithDetails(ours.getId())).isEmpty();
    }

    @Test
    void createBug_withAnotherProjectsTestRun_isNotFound() throws Exception {
        createBug("\"testRunId\":\"" + theirRunId + "\"")
                .andExpect(status().isNotFound());
    }

    @Test
    void createBug_withNonMemberAssignee_isNotFound() throws Exception {
        createBug("\"assigneeId\":\"" + outsiderId + "\"")
                .andExpect(status().isNotFound());
    }

    /**
     * Previously a `200` with the link silently dropped — the caller asked to file a bug against a
     * specific failure and got a detached report without being told.
     */
    @Test
    void createBug_withUnknownTestResult_isNotFoundRatherThanSilentlyUnlinked() throws Exception {
        createBug("\"testResultId\":\"" + UUID.randomUUID() + "\"")
                .andExpect(status().isNotFound());

        assertThat(bugReportRepository.findByProjectIdWithDetails(ours.getId())).isEmpty();
    }

    @Test
    void createBug_withOwnProjectIds_stillWorks() throws Exception {
        createBug("\"testResultId\":\"" + ourResultId + "\","
                + "\"testRunId\":\"" + ourRunId + "\","
                + "\"assigneeId\":\"" + ourMemberId + "\"")
                .andExpect(status().isCreated());

        assertThat(bugReportRepository.findByProjectIdWithDetails(ours.getId())).hasSize(1);
    }

    // --- project members -----------------------------------------------------------------------

    /**
     * The worst of the set, and the one furthest from where this audit started.
     *
     * <p>{@code updateRole} and {@code removeMember} accepted a {@code projectId} and never read
     * it. The controller authorizes with {@code requireProjectAdmin(projectId)}, so admin on
     * <em>any</em> project passed the gate and the service then acted on whatever membership row
     * the id named. An admin of one project could promote themselves — or anyone — to ADMIN on
     * another, or revoke a member's access to it.
     *
     * <p>The caller here is a plain project admin rather than a system admin, because a system
     * admin legitimately may act on both projects and would prove nothing.
     */
    @Test
    void updateRole_onAnotherProjectsMembership_isNotFound() throws Exception {
        mockMvc.perform(put("/api/projects/{p}/members/{m}", ours.getId(), theirMembershipId)
                        .with(user(ourAdminId.toString())).with(csrf())
                        .contentType("application/json").content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isNotFound());

        assertThat(projectMemberRepository.findById(theirMembershipId).orElseThrow().getRole())
                .as("their member's role must be untouched").isEqualTo(ProjectRole.VIEWER);
    }

    @Test
    void removeMember_fromAnotherProject_isNotFound() throws Exception {
        mockMvc.perform(delete("/api/projects/{p}/members/{m}", ours.getId(), theirMembershipId)
                        .with(user(ourAdminId.toString())).with(csrf()))
                .andExpect(status().isNotFound());

        assertThat(projectMemberRepository.findById(theirMembershipId))
                .as("their member must not be removed").isPresent();
    }

    // The CI ingestion path has the same hole on its testPlanId query parameter. It authenticates
    // with an API key rather than a session, so that assertion lives in CiIngestionApiTest, where
    // the key harness already exists.

    // --- test plans ----------------------------------------------------------------------------

    @Test
    void createPlan_withNonMemberAssignee_isNotFound() throws Exception {
        mockMvc.perform(post("/api/projects/{p}/test-plans", ours.getId())
                        .with(user(admin)).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"P\",\"assigneeId\":\"" + outsiderId + "\"}"))
                .andExpect(status().isNotFound());
    }

    // --- fixtures ------------------------------------------------------------------------------

    private org.springframework.test.web.servlet.ResultActions createRun(String body) throws Exception {
        return mockMvc.perform(post("/api/projects/{p}/test-runs", ours.getId())
                .with(user(admin)).with(csrf())
                .contentType("application/json").content(body));
    }

    private org.springframework.test.web.servlet.ResultActions createBug(String extraFields) throws Exception {
        return mockMvc.perform(post("/api/projects/{p}/bug-reports", ours.getId())
                .with(user(admin)).with(csrf())
                .contentType("application/json")
                .content("{\"title\":\"Boom\",\"priority\":\"HIGH\"," + extraFields + "}"));
    }

    private User newUser(String name, boolean systemAdmin) {
        User user = new User();
        user.setEmail(name + "-" + UUID.randomUUID() + "@test.local");
        user.setDisplayName(name);
        user.setPasswordHash("x");
        user.setSystemAdmin(systemAdmin);
        return userRepository.save(user);
    }

    private UUID newMembership(User user, Project project, ProjectRole role) {
        ProjectMember membership = new ProjectMember();
        membership.setUser(user);
        membership.setProject(project);
        membership.setRole(role);
        return projectMemberRepository.save(membership).getId();
    }

    private Project newProject(String name, String key, boolean bugReports) {
        Project project = new Project();
        project.setName(name);
        project.setKey(key);
        project.setBugReportsEnabled(bugReports);
        return projectRepository.save(project);
    }

    private TestCase newCase(Project project, String title, String key) {
        TestCase testCase = new TestCase();
        testCase.setProject(project);
        testCase.setTitle(title);
        testCase.setKey(key);
        testCase.setStatus(TestCaseStatus.ACTIVE);
        testCase.setPriority(Priority.MEDIUM);
        return testCaseRepository.save(testCase);
    }

    private TestPlan newPlan(Project project, String name) {
        TestPlan plan = new TestPlan();
        plan.setProject(project);
        plan.setName(name);
        plan.setStatus(TestPlanStatus.OPEN);
        return testPlanRepository.save(plan);
    }

    private TestRun newRun(Project project, String key) {
        TestRun run = new TestRun();
        run.setProject(project);
        run.setName("Run");
        run.setKey(key);
        run.setStatus(TestRunStatus.IN_PROGRESS);
        return testRunRepository.save(run);
    }

    private TestResult newResult(TestRun run, TestCase testCase) {
        TestResult result = new TestResult();
        result.setTestRun(run);
        result.setTestCase(testCase);
        result.setStatus(TestResultStatus.FAILED, null);
        return testResultRepository.save(result);
    }
}
