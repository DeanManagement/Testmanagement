package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.dto.UpdateTestResultRequest;
import com.deanmanagement.testmanagement.project.internal.dto.UpdateTestRunRequest;
import com.deanmanagement.testmanagement.project.internal.dto.apiKey.CreateApiKeyRequest;
import com.deanmanagement.testmanagement.project.internal.dto.TestRunResponse;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.CreateTestCaseRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testplan.CreateTestPlanRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testplan.ReleaseGate;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.CreateTestRunRequest;
import com.deanmanagement.testmanagement.project.internal.entity.BugReport;
import com.deanmanagement.testmanagement.project.internal.entity.BugReportStatus;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectMember;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.Requirement;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import com.deanmanagement.testmanagement.project.internal.repository.BugReportRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectMemberRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.RequirementRepository;
import com.deanmanagement.testmanagement.project.internal.service.ApiKeyService;
import com.deanmanagement.testmanagement.project.internal.service.TestCaseService;
import com.deanmanagement.testmanagement.project.internal.service.TestPlanService;
import com.deanmanagement.testmanagement.project.internal.service.TestRunService;
import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.internal.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The release gate end to end (PRD-037): data, verdicts, roles, and the CI endpoint. */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Transactional
class ReleaseReadinessApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectMemberRepository memberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private BugReportRepository bugReportRepository;
    @Autowired private RequirementRepository requirementRepository;
    @Autowired private ApiKeyService apiKeyService;
    @Autowired private TestPlanService testPlanService;
    @Autowired private TestCaseService testCaseService;
    @Autowired private TestRunService testRunService;
    @Autowired private EntityManager entityManager;

    private Project project;
    private Project other;
    private UUID viewer;
    private UUID tester;
    private UUID outsider;

    @BeforeEach
    void setUp() {
        project = project("RRG");
        other = project("RRO");
        viewer = member(project, ProjectRole.VIEWER);
        tester = member(project, ProjectRole.TESTER);
        outsider = member(other, ProjectRole.ADMIN);
    }

    private Project project(String key) {
        Project p = new Project();
        p.setName(key);
        p.setKey(key);
        return projectRepository.save(p);
    }

    private UUID member(Project target, ProjectRole role) {
        User user = new User();
        user.setEmail("u-" + UUID.randomUUID() + "@test.local");
        user.setDisplayName("u");
        user.setPasswordHash("x");
        user = userRepository.save(user);
        ProjectMember m = new ProjectMember();
        m.setUser(user);
        m.setProject(target);
        m.setRole(role);
        memberRepository.save(m);
        return user.getId();
    }

    private static RequestPostProcessor as(UUID userId) {
        return authentication(new UsernamePasswordAuthenticationToken(userId.toString(), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private UUID plan(ReleaseGate gate) {
        return testPlanService.create(project.getId(), new CreateTestPlanRequest("Release 3.2", null, null, null, gate),
                null).id();
    }

    private UUID newCase(String title) {
        return testCaseService.create(project.getId(), new CreateTestCaseRequest(title, null, null, Priority.MEDIUM,
                TestCaseStatus.DRAFT, null, null, null), null).id();
    }

    /** A run in the plan with one result per case, set to the given statuses in order. */
    private TestRunResponse run(UUID planId, List<UUID> cases, TestResultStatus... statuses) {
        TestRunResponse run = testRunService.create(project.getId(),
                new CreateTestRunRequest("Run", null, Set.copyOf(cases), planId, null), null);
        entityManager.flush();
        entityManager.clear();
        run = testRunService.findById(project.getId(), run.id());
        for (int i = 0; i < cases.size(); i++) {
            UUID caseId = cases.get(i);
            UUID resultId = run.results().stream().filter(r -> r.testCaseId().equals(caseId)).findFirst().orElseThrow().id();
            testRunService.updateResult(project.getId(), run.id(), resultId,
                    new UpdateTestResultRequest(statuses[i], null, null), null);
        }
        entityManager.flush();
        return run;
    }

    private void bug(Project target, Priority priority, BugReportStatus status) {
        BugReport bug = new BugReport();
        bug.setKey("BUG-" + UUID.randomUUID());
        bug.setProject(target);
        bug.setTitle("Bug");
        bug.setPriority(priority);
        bug.setStatus(status);
        bugReportRepository.save(bug);
    }

    private ResultActions readiness(UUID planId, UUID userId) throws Exception {
        return mockMvc.perform(get("/api/projects/" + project.getId() + "/test-plans/" + planId + "/readiness")
                .with(as(userId)));
    }

    @Nested
    class Criteria {

        @Test
        void aRetestThatPassedMakesThePlanGo() throws Exception {
            UUID planId = plan(new ReleaseGate(new BigDecimal("100"), null, null, null));
            UUID a = newCase("A");
            run(planId, List.of(a), TestResultStatus.FAILED);
            run(planId, List.of(a), TestResultStatus.PASSED);

            readiness(planId, viewer)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.verdict").value("GO"))
                    .andExpect(jsonPath("$.criteria[0].name").value("PASS_RATE"))
                    .andExpect(jsonPath("$.criteria[0].actual").value(100.0))
                    .andExpect(jsonPath("$.counts.considered").value(1));
        }

        @Test
        void anAbortedRunIsIgnored() throws Exception {
            UUID planId = plan(new ReleaseGate(new BigDecimal("100"), null, null, null));
            UUID a = newCase("A");
            run(planId, List.of(a), TestResultStatus.PASSED);
            TestRunResponse aborted = run(planId, List.of(a), TestResultStatus.FAILED);
            testRunService.update(project.getId(), aborted.id(),
                    new UpdateTestRunRequest(null, null, TestRunStatus.ABORTED, null, null, null, null, "Environment down"), null);
            entityManager.flush();

            readiness(planId, viewer).andExpect(jsonPath("$.verdict").value("GO"));
        }

        @Test
        void pendingResultsCountAsNotPassed() throws Exception {
            UUID planId = plan(new ReleaseGate(new BigDecimal("100"), null, null, null));
            run(planId, List.of(newCase("A"), newCase("B")), TestResultStatus.PASSED, TestResultStatus.PENDING);

            readiness(planId, viewer)
                    .andExpect(jsonPath("$.verdict").value("NO_GO"))
                    .andExpect(jsonPath("$.criteria[0].actual").value(50.0))
                    .andExpect(jsonPath("$.counts.pending").value(1));
        }

        @Test
        void onlyOpenCriticalBugsInThisProjectBlock() throws Exception {
            UUID planId = plan(new ReleaseGate(null, 1, null, null));
            bug(project, Priority.CRITICAL, BugReportStatus.OPEN);
            bug(project, Priority.CRITICAL, BugReportStatus.RESOLVED);
            bug(project, Priority.CRITICAL, BugReportStatus.CLOSED);
            bug(project, Priority.HIGH, BugReportStatus.OPEN);
            bug(other, Priority.CRITICAL, BugReportStatus.OPEN);

            readiness(planId, viewer)
                    .andExpect(jsonPath("$.verdict").value("GO"))
                    .andExpect(jsonPath("$.criteria[0].actual").value(1));

            bug(project, Priority.CRITICAL, BugReportStatus.IN_PROGRESS);
            readiness(planId, viewer).andExpect(jsonPath("$.verdict").value("NO_GO"));
        }

        @Test
        void coverageDoesNotApplyWithoutRequirementsAndCountsWithThem() throws Exception {
            UUID planId = plan(new ReleaseGate(null, null, new BigDecimal("50"), null));
            readiness(planId, viewer)
                    .andExpect(jsonPath("$.verdict").value("GO"))
                    .andExpect(jsonPath("$.criteria[0].outcome").value("NOT_APPLICABLE"))
                    .andExpect(jsonPath("$.criteria[0].actual").doesNotExist());

            Requirement requirement = new Requirement();
            requirement.setProjectId(project.getId());
            requirement.setExternalId("REQ-1");
            requirement.setTitle("Pay by card");
            requirementRepository.save(requirement);

            readiness(planId, viewer)
                    .andExpect(jsonPath("$.verdict").value("NO_GO"))
                    .andExpect(jsonPath("$.criteria[0].actual").value(0.0));
        }

        @Test
        void aPlanWithoutAGateHasNoCriteria() throws Exception {
            UUID planId = plan(null);

            readiness(planId, viewer)
                    .andExpect(jsonPath("$.verdict").value("NO_CRITERIA"))
                    .andExpect(jsonPath("$.criteria").isEmpty());
        }
    }

    @Nested
    class Access {

        @Test
        void aNonMemberCannotRead() throws Exception {
            readiness(plan(null), outsider).andExpect(status().is4xxClientError());
        }

        @Test
        void anotherProjectsPlanIsNotFound() throws Exception {
            UUID foreign = testPlanService.create(other.getId(), new CreateTestPlanRequest("Theirs", null, null, null),
                    null).id();

            readiness(foreign, viewer).andExpect(status().isNotFound());
        }
    }

    @Nested
    class GateEdits {

        private ResultActions update(UUID planId, String gateJson) throws Exception {
            return mockMvc.perform(put("/api/projects/" + project.getId() + "/test-plans/" + planId)
                    .with(as(tester)).with(csrf()).contentType("application/json")
                    .content("{\"name\":\"Release 3.2\"" + gateJson + "}"));
        }

        @Test
        void aGateIsSavedAndReturned() throws Exception {
            update(plan(null), ",\"gate\":{\"minPassRate\":98.5,\"maxBlockerBugs\":0}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.gate.minPassRate").value(98.5))
                    .andExpect(jsonPath("$.gate.maxBlockerBugs").value(0))
                    .andExpect(jsonPath("$.gate.minCoverage").doesNotExist());
        }

        @Test
        void anUpdateWithoutAGateLeavesItAlone() throws Exception {
            UUID planId = plan(new ReleaseGate(new BigDecimal("90"), null, null, null));

            update(planId, "").andExpect(status().isOk()).andExpect(jsonPath("$.gate.minPassRate").value(90.0));
        }

        @Test
        void outOfRangeThresholdsAreRefused() throws Exception {
            UUID planId = plan(null);

            update(planId, ",\"gate\":{\"minPassRate\":120}").andExpect(status().isBadRequest());
            update(planId, ",\"gate\":{\"maxBlockerBugs\":-1}").andExpect(status().isBadRequest());
            update(planId, ",\"gate\":{\"minCoverage\":50.123}").andExpect(status().isBadRequest());
        }
    }

    @Nested
    class ExternalGate {

        private String key;

        @BeforeEach
        void viewerKey() {
            key = apiKeyService.create(new CreateApiKeyRequest("ci-gate", project.getId(), ProjectRole.VIEWER)).rawKey();
        }

        private ResultActions gate(String projectRef, UUID planId, boolean enforce) throws Exception {
            return mockMvc.perform(get("/api/external/projects/" + projectRef + "/test-plans/" + planId + "/readiness")
                    .param("enforce", String.valueOf(enforce))
                    .header("X-API-Key", key));
        }

        private UUID failingPlan() {
            UUID planId = plan(new ReleaseGate(new BigDecimal("100"), null, null, null));
            run(planId, List.of(newCase("A")), TestResultStatus.FAILED);
            return planId;
        }

        @Test
        void aNoGoIsReportedAsOkWithoutEnforce() throws Exception {
            gate(project.getKey(), failingPlan(), false)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.verdict").value("NO_GO"));
        }

        @Test
        void aNoGoFailsThePipelineWithEnforceAndSaysWhy() throws Exception {
            gate(project.getKey(), failingPlan(), true)
                    .andExpect(status().isPreconditionFailed())
                    .andExpect(jsonPath("$.verdict").value("NO_GO"))
                    .andExpect(jsonPath("$.criteria[0].outcome").value("FAIL"));
        }

        @Test
        void aGoPassesWithEnforce() throws Exception {
            UUID planId = plan(new ReleaseGate(new BigDecimal("100"), null, null, null));
            run(planId, List.of(newCase("A")), TestResultStatus.PASSED);

            gate(project.getId().toString(), planId, true).andExpect(status().isOk());
        }

        @Test
        void noCriteriaFailsThePipelineWithEnforce() throws Exception {
            gate(project.getKey(), plan(null), true)
                    .andExpect(status().isPreconditionFailed())
                    .andExpect(jsonPath("$.verdict").value("NO_CRITERIA"));
        }

        @Test
        void aKeyForAnotherProjectIsRejected() throws Exception {
            gate(other.getKey(), plan(null), false).andExpect(status().is4xxClientError());
        }

        @Test
        void aPlanOfAnotherProjectIsNotFound() throws Exception {
            UUID foreign = testPlanService.create(other.getId(), new CreateTestPlanRequest("Theirs", null, null, null),
                    null).id();

            gate(project.getKey(), foreign, false).andExpect(status().isNotFound());
        }
    }
}
