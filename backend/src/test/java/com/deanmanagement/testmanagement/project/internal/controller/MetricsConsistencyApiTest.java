package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectMember;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectMemberRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.internal.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PRD-049: one pass-rate definition everywhere (passed of executed), a progress figure beside it, and
 * no 0 % where nothing was executed. The release gate keeps its own rule and is not tested here.
 */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Transactional
class MetricsConsistencyApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private ProjectMemberRepository projectMemberRepository;
    @Autowired
    private EntityManager entityManager;

    private String tester;
    private UUID projectId;
    private UUID planId;
    private final List<UUID> caseIds = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        User user = new User();
        user.setEmail("tess-" + UUID.randomUUID() + "@test.local");
        user.setDisplayName("Tess");
        user.setPasswordHash("x");
        user = userRepository.save(user);
        tester = user.getId().toString();
        Project project = new Project();
        project.setName("Metrics");
        project.setKey("MET");
        project = projectRepository.save(project);
        projectId = project.getId();
        ProjectMember membership = new ProjectMember();
        membership.setUser(user);
        membership.setProject(project);
        membership.setRole(ProjectRole.TESTER);
        projectMemberRepository.save(membership);

        planId = id(send(post(url("/test-plans")), "{\"name\":\"2.1\"}"));
        for (int i = 0; i < 4; i++) {
            caseIds.add(id(send(post(url("/test-cases")), "{\"title\":\"Case " + i + "\",\"priority\":\"LOW\",\"status\":\"DRAFT\"}")));
        }
    }

    @Test
    void aPlanWithOnePassAndThreePendingIsAtFullPassRateAndAQuarterDone() throws Exception {
        UUID run = startRun();
        record(run, 0, "PASSED");
        // The plan was loaded before the run existed; in one test transaction its runs are cached.
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get(url("/test-plans/" + planId + "/summary")).with(user(tester)))
                .andExpect(jsonPath("$.passRate").value(100.0))
                .andExpect(jsonPath("$.executed").value(1))
                .andExpect(jsonPath("$.progress").value(25.0))
                .andExpect(jsonPath("$.runs[0].key").value("MET-Run-1"))
                .andExpect(jsonPath("$.runs[0].executed").value(1))
                .andExpect(jsonPath("$.runs[0].passRate").value(100.0));
    }

    @Test
    void theRunReportUsesTheSameDefinitionWithProgress() throws Exception {
        UUID run = startRun();
        record(run, 0, "PASSED");
        record(run, 1, "FAILED");

        mockMvc.perform(get(url("/test-runs/" + run + "/report")).with(user(tester)))
                .andExpect(jsonPath("$.passRate").value(50.0))
                .andExpect(jsonPath("$.progress").value(50.0));
    }

    @Test
    void aCompletedRunThatExecutedNothingIsAGapNotZero() throws Exception {
        UUID run = startRun();
        complete(run);

        mockMvc.perform(get(url("/dashboard")).with(user(tester)))
                .andExpect(jsonPath("$.passRateTrend[0].passRate").doesNotExist())
                .andExpect(jsonPath("$.overallPassRate").doesNotExist());
    }

    @Test
    void aProjectWhereEverythingFailedIsAtZeroNotBlank() throws Exception {
        UUID run = startRun();
        for (int i = 0; i < caseIds.size(); i++) {
            record(run, i, "FAILED");
        }
        complete(run);

        mockMvc.perform(get(url("/dashboard")).with(user(tester)))
                .andExpect(jsonPath("$.overallPassRate").value(0.0))
                .andExpect(jsonPath("$.passRateTrend[0].passRate").value(0.0));
    }

    @Test
    void theSuiteReportCountsUntestedCasesTowardProgressOnly() throws Exception {
        UUID suite = id(send(post(url("/test-suites")), "{\"name\":\"Smoke\",\"testCaseIds\":[\"" + caseIds.get(0)
                + "\",\"" + caseIds.get(1) + "\"]}"));
        UUID run = startRun();
        record(run, 0, "PASSED");
        complete(run);

        mockMvc.perform(get(url("/test-suites/" + suite + "/report")).with(user(tester)))
                .andExpect(jsonPath("$.passRate").value(100.0))
                .andExpect(jsonPath("$.progress").value(50.0));
    }

    // ---- helpers --------------------------------------------------------------------------------

    private UUID startRun() throws Exception {
        String ids = String.join("\",\"", caseIds.stream().map(UUID::toString).toList());
        return id(send(post(url("/test-runs")), "{\"name\":\"Run\",\"testPlanId\":\"" + planId
                + "\",\"testCaseIds\":[\"" + ids + "\"]}"));
    }

    private void record(UUID run, int caseIndex, String status) throws Exception {
        String detail = mockMvc.perform(get(url("/test-runs/" + run)).with(user(tester))).andReturn().getResponse()
                .getContentAsString();
        List<String> ids = JsonPath.read(detail, "$.results[?(@.testCaseId=='" + caseIds.get(caseIndex) + "')].id");
        send(put(url("/test-runs/" + run + "/results/" + ids.getFirst())), "{\"status\":\"" + status + "\"}")
                .andExpect(status().isOk());
    }

    private void complete(UUID run) throws Exception {
        send(put(url("/test-runs/" + run)), "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());
        send(put(url("/test-runs/" + run)), "{\"status\":\"COMPLETED\"}").andExpect(status().isOk());
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mockMvc.perform(request.with(user(tester)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static UUID id(ResultActions created) throws Exception {
        return UUID.fromString(JsonPath.read(created.andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString(), "$.id"));
    }

    private String url(String path) {
        return "/api/projects/" + projectId + path;
    }
}
