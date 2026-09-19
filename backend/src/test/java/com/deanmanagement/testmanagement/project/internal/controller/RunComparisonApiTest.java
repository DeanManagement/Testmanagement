package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.ci.CiResult;
import com.deanmanagement.testmanagement.project.internal.dto.TestRunResponse;
import com.deanmanagement.testmanagement.project.internal.dto.UpdateTestResultRequest;
import com.deanmanagement.testmanagement.project.internal.dto.UpdateTestRunRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.CreateTestCaseRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testplan.CreateTestPlanRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.CreateTestRunRequest;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectMember;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectMemberRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.service.CiIngestionService;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Run comparison end to end (PRD-038): the automatic base, CI runs, access and options. */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Transactional
class RunComparisonApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectMemberRepository memberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TestCaseService testCaseService;
    @Autowired private TestRunService testRunService;
    @Autowired private TestPlanService testPlanService;
    @Autowired private CiIngestionService ciIngestionService;
    @Autowired private EntityManager entityManager;

    private Project project;
    private Project other;
    private UUID viewer;
    private UUID outsider;
    private UUID checkout;
    private UUID search;

    @BeforeEach
    void setUp() {
        project = project("RCP");
        other = project("RCO");
        viewer = member(project, ProjectRole.VIEWER);
        outsider = member(other, ProjectRole.ADMIN);
        checkout = newCase("Checkout");
        search = newCase("Search");
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

    private UUID newCase(String title) {
        return testCaseService.create(project.getId(), new CreateTestCaseRequest(title, null, null, Priority.MEDIUM,
                TestCaseStatus.DRAFT, null, null, null), null).id();
    }

    /** A run of both cases, recorded with the given statuses. */
    private UUID run(String name, UUID planId, String environment, TestResultStatus checkoutStatus,
                     TestResultStatus searchStatus) {
        TestRunResponse run = testRunService.create(project.getId(),
                new CreateTestRunRequest(name, environment, Set.of(checkout, search), planId, null), null);
        entityManager.flush();
        entityManager.clear();
        run = testRunService.findById(project.getId(), run.id());
        Map<UUID, TestResultStatus> statuses = Map.of(checkout, checkoutStatus, search, searchStatus);
        for (var result : run.results()) {
            testRunService.updateResult(project.getId(), run.id(), result.id(),
                    new UpdateTestResultRequest(statuses.get(result.testCaseId()), null, null));
        }
        entityManager.flush();
        return run.id();
    }

    private void abort(UUID runId) {
        testRunService.update(project.getId(), runId, new UpdateTestRunRequest(null, null, TestRunStatus.ABORTED, null,
                null, null, null, "Environment down"), null);
        entityManager.flush();
    }

    private MockHttpServletRequestBuilder compare(UUID head) {
        return get("/api/projects/" + project.getId() + "/test-runs/compare").param("head", head.toString());
    }

    private ResultActions compare(UUID head, UUID userId) throws Exception {
        return mockMvc.perform(compare(head).with(as(userId)));
    }

    @Nested
    class Comparison {

        @Test
        void classifiesWhatChangedAndListsOnlyChangesByDefault() throws Exception {
            UUID base = run("nightly", null, null, TestResultStatus.PASSED, TestResultStatus.PASSED);
            UUID head = run("nightly", null, null, TestResultStatus.FAILED, TestResultStatus.PASSED);

            compare(head, viewer)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.base.id").value(base.toString()))
                    .andExpect(jsonPath("$.baseAutoSelected").value(true))
                    .andExpect(jsonPath("$.counts.newlyFailing").value(1))
                    .andExpect(jsonPath("$.counts.unchanged").value(1))
                    .andExpect(jsonPath("$.rows.length()").value(1))
                    .andExpect(jsonPath("$.rows[0].category").value("NEWLY_FAILING"))
                    .andExpect(jsonPath("$.rows[0].title").value("Checkout"));
        }

        @Test
        void includeUnchangedListsTheUnchangedRowsToo() throws Exception {
            run("nightly", null, null, TestResultStatus.PASSED, TestResultStatus.PASSED);
            UUID head = run("nightly", null, null, TestResultStatus.FAILED, TestResultStatus.PASSED);

            mockMvc.perform(compare(head).param("includeUnchanged", "true").with(as(viewer)))
                    .andExpect(jsonPath("$.rows.length()").value(2))
                    .andExpect(jsonPath("$.rows[1].category").value("UNCHANGED"));
        }

        @Test
        void anExplicitBaseIsUsedEvenIfItIsNewer() throws Exception {
            UUID head = run("nightly", null, null, TestResultStatus.PASSED, TestResultStatus.PASSED);
            UUID newer = run("other", null, null, TestResultStatus.FAILED, TestResultStatus.PASSED);

            mockMvc.perform(compare(head).param("base", newer.toString()).with(as(viewer)))
                    .andExpect(jsonPath("$.base.id").value(newer.toString()))
                    .andExpect(jsonPath("$.baseAutoSelected").value(false))
                    .andExpect(jsonPath("$.counts.fixed").value(1));
        }

        @Test
        void twoCiUploadsOfTheSameTestsCompareByCaseNotAsAllAdded() throws Exception {
            ciIngestionService.ingest(project.getKey(), "CI", null, null, List.of(
                    new CiResult("suite", "com.shop.CartTest.adds", TestResultStatus.PASSED, null, List.of(), null),
                    new CiResult("suite", "com.shop.CartTest.removes", TestResultStatus.PASSED, null, List.of(), null)),
                    null);
            UUID head = ciIngestionService.ingest(project.getKey(), "CI", null, null, List.of(
                    new CiResult("suite", "com.shop.CartTest.adds", TestResultStatus.FAILED, "boom", List.of(), null),
                    new CiResult("suite", "com.shop.CartTest.removes", TestResultStatus.PASSED, null, List.of(), null)),
                    null).id();
            entityManager.flush();

            compare(head, viewer)
                    .andExpect(jsonPath("$.counts.added").value(0))
                    .andExpect(jsonPath("$.counts.newlyFailing").value(1))
                    .andExpect(jsonPath("$.counts.unchanged").value(1));
        }
    }

    @Nested
    class AutomaticBase {

        @Test
        void preferTheSameNameOverTheSamePlanAndEnvironment() throws Exception {
            UUID plan = testPlanService.create(project.getId(), new CreateTestPlanRequest("R1", null, null, null), null).id();
            UUID sameName = run("nightly", null, "staging", TestResultStatus.PASSED, TestResultStatus.PASSED);
            run("manual", plan, "staging", TestResultStatus.PASSED, TestResultStatus.PASSED);
            UUID head = run("nightly", plan, "staging", TestResultStatus.PASSED, TestResultStatus.PASSED);

            compare(head, viewer).andExpect(jsonPath("$.base.id").value(sameName.toString()));
        }

        @Test
        void fallBackToTheSamePlanAndEnvironment() throws Exception {
            UUID plan = testPlanService.create(project.getId(), new CreateTestPlanRequest("R1", null, null, null), null).id();
            run("regression", plan, "prod", TestResultStatus.PASSED, TestResultStatus.PASSED);
            UUID samePlan = run("regression 1", plan, "staging", TestResultStatus.PASSED, TestResultStatus.PASSED);
            UUID head = run("regression 2", plan, "staging", TestResultStatus.PASSED, TestResultStatus.PASSED);

            compare(head, viewer).andExpect(jsonPath("$.base.id").value(samePlan.toString()));
        }

        @Test
        void skipAbortedRuns() throws Exception {
            UUID older = run("nightly", null, null, TestResultStatus.PASSED, TestResultStatus.PASSED);
            UUID aborted = run("nightly", null, null, TestResultStatus.FAILED, TestResultStatus.PASSED);
            abort(aborted);
            // The aborted run is the latest one before the head, so only its status can exclude it.
            UUID head = run("nightly", null, null, TestResultStatus.PASSED, TestResultStatus.PASSED);

            compare(head, viewer).andExpect(jsonPath("$.base.id").value(older.toString()));
        }

        @Test
        void neverPickALaterRun() throws Exception {
            UUID head = run("nightly", null, null, TestResultStatus.PASSED, TestResultStatus.PASSED);
            run("nightly", null, null, TestResultStatus.FAILED, TestResultStatus.PASSED);

            compare(head, viewer)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message", containsString("pick a base run")));
        }

        @Test
        void aRunWithoutAPlanOnlyMatchesByName() throws Exception {
            run("exploratory 1", null, null, TestResultStatus.PASSED, TestResultStatus.PASSED);
            UUID head = run("exploratory 2", null, null, TestResultStatus.PASSED, TestResultStatus.PASSED);

            compare(head, viewer).andExpect(status().isNotFound());
        }
    }

    @Nested
    class Access {

        @Test
        void theSameRunTwiceIsABadRequest() throws Exception {
            UUID head = run("nightly", null, null, TestResultStatus.PASSED, TestResultStatus.PASSED);

            mockMvc.perform(compare(head).param("base", head.toString()).with(as(viewer)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void aNonMemberCannotCompare() throws Exception {
            UUID head = run("nightly", null, null, TestResultStatus.PASSED, TestResultStatus.PASSED);

            compare(head, outsider).andExpect(status().is4xxClientError());
        }

        @Test
        void aRunOfAnotherProjectIsNotFound() throws Exception {
            UUID head = run("nightly", null, null, TestResultStatus.PASSED, TestResultStatus.PASSED);
            UUID foreign = testRunService.create(other.getId(), new CreateTestRunRequest("theirs", null, null, null, null),
                    null).id();

            mockMvc.perform(compare(head).param("base", foreign.toString()).with(as(viewer)))
                    .andExpect(status().isNotFound());
            compare(foreign, viewer).andExpect(status().isNotFound());
        }
    }
}
