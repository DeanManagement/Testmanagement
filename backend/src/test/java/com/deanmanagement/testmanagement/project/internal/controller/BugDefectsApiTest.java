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
import org.junit.jupiter.api.Nested;
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

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PRD-047: defects where testing happens. A bug keeps the result and step it was found in, can be
 * linked to later occurrences, shows up on the plan whose runs it touched, and feeds the dashboard.
 */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Transactional
class BugDefectsApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    private String tester;
    private UUID projectId;
    private UUID otherProjectId;

    /** A run of two steps, in {@code planId}: its one result and that result's step results. */
    private UUID planId;
    private UUID runId;
    private UUID resultId;
    private List<String> stepIds;

    @BeforeEach
    void setUp() throws Exception {
        User testerUser = new User();
        testerUser.setEmail("tess-" + UUID.randomUUID() + "@test.local");
        testerUser.setDisplayName("Tess");
        testerUser.setPasswordHash("x");
        testerUser = userRepository.save(testerUser);
        tester = testerUser.getId().toString();

        projectId = saveProject("Defects", "DEF", testerUser);
        otherProjectId = saveProject("Other", "ODF", testerUser);

        planId = create(projectId, "/test-plans", "{\"name\":\"2.1\"}");
        UUID testCase = create(projectId, "/test-cases", "{\"title\":\"Checkout\",\"priority\":\"HIGH\",\"status\":\"DRAFT\","
                + "\"steps\":[{\"action\":\"Add to cart\"},{\"action\":\"Pay\"}]}");
        String run = send(post(url(projectId, "/test-runs")), "{\"name\":\"Nightly\",\"testPlanId\":\"" + planId
                + "\",\"testCaseIds\":[\"" + testCase + "\"]}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        runId = UUID.fromString(JsonPath.read(run, "$.id"));
        String runDetail = mockMvc.perform(get(url(projectId, "/test-runs/" + runId)).with(user(tester)))
                .andReturn().getResponse().getContentAsString();
        resultId = UUID.fromString(JsonPath.read(runDetail, "$.results[0].id"));
        stepIds = JsonPath.read(runDetail, "$.results[0].stepResults[*].id");
    }

    @Nested
    class FoundIn {

        @Test
        void aBugKeepsTheStepItWasFoundIn() throws Exception {
            fileBug("{\"title\":\"Payment fails\",\"priority\":\"HIGH\",\"testResultId\":\"" + resultId
                    + "\",\"stepResultId\":\"" + stepIds.get(1) + "\"}")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.stepNumber").value(2))
                    .andExpect(jsonPath("$.testCaseKey").value("DEF-1"))
                    .andExpect(jsonPath("$.testCaseId").isString());
        }

        @Test
        void aStepOfAnotherResultIsRefused() throws Exception {
            fileBug("{\"title\":\"x\",\"priority\":\"HIGH\",\"stepResultId\":\"" + stepIds.get(0) + "\"}")
                    .andExpect(status().isBadRequest());
            fileBug("{\"title\":\"x\",\"priority\":\"HIGH\",\"testResultId\":\"" + resultId
                    + "\",\"stepResultId\":\"" + UUID.randomUUID() + "\"}")
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class Links {

        @Test
        void anExistingBugIsLinkedToALaterResultAndKeepsItsOrigin() throws Exception {
            UUID bug = id(fileBug("{\"title\":\"Old crash\",\"priority\":\"HIGH\"}"));

            link(bug, resultId, stepIds.get(0))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.testResultId").doesNotExist())
                    .andExpect(jsonPath("$.links", hasSize(1)))
                    .andExpect(jsonPath("$.links[0].testResultId").value(resultId.toString()))
                    .andExpect(jsonPath("$.links[0].stepNumber").value(1))
                    .andExpect(jsonPath("$.links[0].testRunKey").isString());
        }

        @Test
        void linkingTwiceOrLinkingTheOriginChangesNothing() throws Exception {
            UUID linked = id(fileBug("{\"title\":\"Linked\",\"priority\":\"HIGH\"}"));
            link(linked, resultId, null);
            link(linked, resultId, null).andExpect(jsonPath("$.links", hasSize(1)));

            UUID foundHere = id(fileBug("{\"title\":\"Found here\",\"priority\":\"HIGH\",\"testResultId\":\"" + resultId + "\"}"));
            link(foundHere, resultId, null).andExpect(jsonPath("$.links", hasSize(0)));
        }

        @Test
        void aResultOfAnotherProjectIsNotFound() throws Exception {
            UUID bug = id(fileBug("{\"title\":\"Mine\",\"priority\":\"HIGH\"}"));

            mockMvc.perform(post("/api/projects/" + otherProjectId + "/bug-reports/" + bug + "/links").with(user(tester))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"testResultId\":\"" + resultId + "\"}"))
                    .andExpect(status().isNotFound());
        }

        @Test
        void theResultsBugsIncludeLinkedOnes() throws Exception {
            UUID found = id(fileBug("{\"title\":\"Found here\",\"priority\":\"HIGH\",\"testResultId\":\"" + resultId + "\"}"));
            UUID linked = id(fileBug("{\"title\":\"Seen here too\",\"priority\":\"HIGH\"}"));
            link(linked, resultId, null);
            fileBug("{\"title\":\"Unrelated\",\"priority\":\"HIGH\"}");

            mockMvc.perform(get(url(projectId, "/bug-reports?testResultId=" + resultId)).with(user(tester)))
                    .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(found.toString(), linked.toString())));
        }

        @Test
        void anOccurrenceCanBeUnlinked() throws Exception {
            UUID bug = id(fileBug("{\"title\":\"Linked\",\"priority\":\"HIGH\"}"));
            String linkId = JsonPath.read(link(bug, resultId, null).andReturn().getResponse().getContentAsString(),
                    "$.links[0].id");

            mockMvc.perform(delete(url(projectId, "/bug-reports/" + bug + "/links/" + linkId)).with(user(tester)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.links", hasSize(0)));
        }
    }

    @Nested
    class DefectLinkField {

        @Test
        void aStatusChangeKeepsTheCommentAndDefectLinkAndEmptyTextClearsThem() throws Exception {
            String result = url(projectId, "/test-runs/" + runId + "/results/" + resultId);
            send(put(result),
                    "{\"status\":\"FAILED\",\"comment\":\"500 from pay\",\"defectLink\":\"https://jira/X-1\"}")
                    .andExpect(status().isOk());

            send(put(result), "{\"status\":\"BLOCKED\"}")
                    .andExpect(jsonPath("$.comment").value("500 from pay"))
                    .andExpect(jsonPath("$.defectLink").value("https://jira/X-1"));
            send(put(result),
                    "{\"status\":\"BLOCKED\",\"defectLink\":\"\"}")
                    .andExpect(jsonPath("$.defectLink").doesNotExist());
        }
    }

    @Nested
    class PlanDefects {

        @Test
        void listsBugsFoundInOrLinkedToThePlansRunsOnce() throws Exception {
            fileBug("{\"title\":\"Found in plan\",\"priority\":\"HIGH\",\"testResultId\":\"" + resultId + "\"}");
            UUID linked = id(fileBug("{\"title\":\"Linked in plan\",\"priority\":\"HIGH\"}"));
            link(linked, resultId, null);
            UUID both = id(fileBug("{\"title\":\"Both\",\"priority\":\"HIGH\",\"testResultId\":\"" + resultId + "\"}"));
            link(both, resultId, stepIds.get(0));
            fileBug("{\"title\":\"Elsewhere\",\"priority\":\"HIGH\"}");

            mockMvc.perform(get(url(projectId, "/test-plans/" + planId + "/bug-reports")).with(user(tester)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].title", containsInAnyOrder("Found in plan", "Linked in plan", "Both")));
        }
    }

    @Nested
    class Dashboard {

        @Test
        void countsOpenBugsByStatusAndPriority() throws Exception {
            fileBug("{\"title\":\"A\",\"priority\":\"HIGH\"}");
            fileBug("{\"title\":\"B\",\"priority\":\"CRITICAL\"}");
            close(id(fileBug("{\"title\":\"C\",\"priority\":\"CRITICAL\"}")));

            defects()
                    .andExpect(jsonPath("$.open").value(2))
                    .andExpect(jsonPath("$.byStatus.NEW").value(2))
                    .andExpect(jsonPath("$.byStatus.CLOSED").value(1))
                    .andExpect(jsonPath("$.openByPriority.CRITICAL").value(1))
                    .andExpect(jsonPath("$.openByPriority.LOW").value(0));
        }

        @Test
        void theTrendHasTwelveWeeksWithThisWeeksReportsAndResolutions() throws Exception {
            fileBug("{\"title\":\"A\",\"priority\":\"HIGH\"}");
            close(id(fileBug("{\"title\":\"B\",\"priority\":\"HIGH\"}")));

            defects()
                    .andExpect(jsonPath("$.trend", hasSize(12)))
                    .andExpect(jsonPath("$.trend[0].created").value(0))
                    .andExpect(jsonPath("$.trend[11].created").value(2))
                    .andExpect(jsonPath("$.trend[11].resolved").value(1));
        }

        @Test
        void aReopenedBugNoLongerCountsAsResolved() throws Exception {
            UUID bug = id(fileBug("{\"title\":\"B\",\"priority\":\"HIGH\"}"));
            close(bug);

            send(patch(url(projectId, "/bug-reports/" + bug + "/status")), "{\"status\":\"OPEN\",\"reason\":\"back\"}");

            defects().andExpect(jsonPath("$.trend[11].resolved").value(0));
        }
    }

    // ---- helpers --------------------------------------------------------------------------------

    private ResultActions fileBug(String body) throws Exception {
        return send(post(url(projectId, "/bug-reports")), body);
    }

    private ResultActions link(UUID bug, UUID result, String step) throws Exception {
        String stepJson = step == null ? "" : ",\"stepResultId\":\"" + step + "\"";
        return send(post(url(projectId, "/bug-reports/" + bug + "/links")),
                "{\"testResultId\":\"" + result + "\"" + stepJson + "}");
    }

    private void close(UUID bug) throws Exception {
        send(patch(url(projectId, "/bug-reports/" + bug + "/status")),
                "{\"status\":\"CLOSED\",\"reason\":\"fixed\",\"resolution\":\"FIXED\"}").andExpect(status().isOk());
    }

    private ResultActions defects() throws Exception {
        return mockMvc.perform(get(url(projectId, "/dashboard/defects")).with(user(tester))).andExpect(status().isOk());
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mockMvc.perform(request.with(user(tester)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private UUID create(UUID project, String path, String body) throws Exception {
        return id(send(post(url(project, path)), body).andExpect(status().isCreated()));
    }

    private static UUID id(ResultActions created) throws Exception {
        return UUID.fromString(JsonPath.read(created.andReturn().getResponse().getContentAsString(), "$.id"));
    }

    private static String url(UUID project, String path) {
        return "/api/projects/" + project + path;
    }

    private UUID saveProject(String name, String key, User member) {
        Project project = new Project();
        project.setName(name);
        project.setKey(key);
        project.setBugReportsEnabled(true);
        project = projectRepository.save(project);
        ProjectMember membership = new ProjectMember();
        membership.setUser(member);
        membership.setProject(project);
        membership.setRole(ProjectRole.TESTER);
        projectMemberRepository.save(membership);
        return project.getId();
    }
}
