package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectMember;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectMemberRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.internal.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PRD-048: a result says who executed it and when, carries the case's key and texts, cascades a
 * pass to its pending steps on request, and the report names the run's plan.
 */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Transactional
class ExecutionEvidenceApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    private String tess;
    private String lead;
    private UUID projectId;
    private UUID runId;
    private UUID resultId;
    private List<String> stepIds;

    @BeforeEach
    void setUp() throws Exception {
        Project project = new Project();
        project.setName("Evidence");
        project.setKey("EVI");
        project = projectRepository.save(project);
        projectId = project.getId();
        tess = member(project, "Tess");
        lead = member(project, "Lead");

        UUID plan = id(send(tess, post(url("/test-plans")), "{\"name\":\"2.1\"}"));
        UUID testCase = id(send(tess, post(url("/test-cases")), "{\"title\":\"Checkout\",\"priority\":\"HIGH\","
                + "\"status\":\"DRAFT\",\"preconditions\":\"Logged in\",\"description\":\"Pays by card\","
                + "\"steps\":[{\"action\":\"Add to cart\"},{\"action\":\"Pay\"},{\"action\":\"See receipt\"}]}"));
        runId = id(send(tess, post(url("/test-runs")), "{\"name\":\"Nightly\",\"testPlanId\":\"" + plan
                + "\",\"testCaseIds\":[\"" + testCase + "\"]}"));
        String run = mockMvc.perform(get(url("/test-runs/" + runId)).with(user(tess)))
                .andReturn().getResponse().getContentAsString();
        resultId = UUID.fromString(JsonPath.read(run, "$.results[0].id"));
        stepIds = JsonPath.read(run, "$.results[0].stepResults[*].id");
    }

    @Test
    void theFirstStatusRecordsTheExecutorAndALaterEditDoesNotChangeIt() throws Exception {
        updateResult(tess, "{\"status\":\"FAILED\"}")
                .andExpect(jsonPath("$.executedByName").value("Tess"))
                .andExpect(jsonPath("$.executedAt").isString());

        updateResult(lead, "{\"status\":\"FAILED\",\"comment\":\"typo fixed\"}")
                .andExpect(jsonPath("$.executedByName").value("Tess"));
    }

    @Test
    void resettingToPendingClearsTheExecutor() throws Exception {
        updateResult(tess, "{\"status\":\"PASSED\"}");

        updateResult(lead, "{\"status\":\"PENDING\"}")
                .andExpect(jsonPath("$.executedBy").doesNotExist())
                .andExpect(jsonPath("$.executedAt").doesNotExist());
    }

    @Test
    void theResultCarriesTheCasesKeyAndTexts() throws Exception {
        updateResult(tess, "{\"status\":\"PASSED\"}")
                .andExpect(jsonPath("$.testCaseKey").value("EVI-1"))
                .andExpect(jsonPath("$.testCasePreconditions").value("Logged in"))
                .andExpect(jsonPath("$.testCaseDescription").value("Pays by card"));
    }

    @Test
    void aPassCascadesOnlyToStepsStillPending() throws Exception {
        send(tess, put(url("/test-runs/" + runId + "/results/" + resultId + "/steps/" + stepIds.get(1))),
                "{\"status\":\"SKIPPED\"}").andExpect(status().isOk());

        updateResult(tess, "{\"status\":\"PASSED\",\"cascadeSteps\":true}")
                .andExpect(jsonPath("$.stepResults[*].status", contains("PASSED", "SKIPPED", "PASSED")));
    }

    @Test
    void aFailureDoesNotCascade() throws Exception {
        updateResult(tess, "{\"status\":\"FAILED\",\"cascadeSteps\":true}").andExpect(status().isBadRequest());
    }

    @Test
    void theReportNamesThePlanAndWhoExecutedEachResult() throws Exception {
        updateResult(tess, "{\"status\":\"PASSED\"}");

        mockMvc.perform(get(url("/test-runs/" + runId + "/report")).with(user(lead)))
                .andExpect(jsonPath("$.testPlanName").value("2.1"))
                .andExpect(jsonPath("$.results[0].testCaseKey").value("EVI-1"))
                .andExpect(jsonPath("$.results[0].executedByName").value("Tess"));
    }

    @Test
    void thePdfWithStepsAndScreenshotsRenders() throws Exception {
        updateResult(tess, "{\"status\":\"PASSED\",\"cascadeSteps\":true}");

        mockMvc.perform(get(url("/test-runs/" + runId + "/report/pdf?steps=true&screenshots=true")).with(user(lead)))
                .andExpect(status().isOk())
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                        result.getResponse().getContentAsByteArray()).startsWith("%PDF".getBytes()));
    }

    // ---- helpers --------------------------------------------------------------------------------

    private ResultActions updateResult(String who, String body) throws Exception {
        return send(who, put(url("/test-runs/" + runId + "/results/" + resultId)), body);
    }

    private ResultActions send(String who, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                               String body) throws Exception {
        return mockMvc.perform(request.with(user(who)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static UUID id(ResultActions created) throws Exception {
        return UUID.fromString(JsonPath.read(created.andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString(), "$.id"));
    }

    private String url(String path) {
        return "/api/projects/" + projectId + path;
    }

    private String member(Project project, String name) {
        User user = new User();
        user.setEmail(name.toLowerCase() + "-" + UUID.randomUUID() + "@test.local");
        user.setDisplayName(name);
        user.setPasswordHash("x");
        user = userRepository.save(user);
        ProjectMember membership = new ProjectMember();
        membership.setUser(user);
        membership.setProject(project);
        membership.setRole(ProjectRole.TESTER);
        projectMemberRepository.save(membership);
        return user.getId().toString();
    }
}
