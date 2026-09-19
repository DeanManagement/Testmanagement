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
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PRD-046: updates record what they changed, comments say where they are, entries link to live
 * objects, and the activity can be filtered, sorted and exported.
 */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Transactional
class AuditTrailApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    private String tester;
    private String viewer;
    private String outsider;
    private UUID testerId;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        User testerUser = saveUser("Tess");
        User viewerUser = saveUser("Vic");
        tester = testerUser.getId().toString();
        testerId = testerUser.getId();
        viewer = viewerUser.getId().toString();
        outsider = saveUser("Otto").getId().toString();

        Project project = new Project();
        project.setName("Audit");
        project.setKey("AUD");
        project.setBugReportsEnabled(true);
        project = projectRepository.save(project);
        projectId = project.getId();
        saveMember(testerUser, project, ProjectRole.TESTER);
        saveMember(viewerUser, project, ProjectRole.VIEWER);
    }

    @Nested
    class FieldChangesAreRecorded {

        @Test
        void aBugEditRecordsEachChangedFieldAndWhoChangedIt() throws Exception {
            UUID bug = create("/bug-reports", "{\"title\":\"Crash\",\"priority\":\"MEDIUM\"}");

            send(put(url("/bug-reports/" + bug)), "{\"title\":\"Crash\",\"priority\":\"HIGH\",\"assigneeId\":\""
                    + testerId + "\"}")
                    .andExpect(jsonPath("$.updatedByName").value("Tess"));

            history(bug)
                    .andExpect(jsonPath("$.content[0].action").value("UPDATED"))
                    .andExpect(jsonPath("$.content[0].changes[*].field", contains("priority", "assignee")))
                    .andExpect(jsonPath("$.content[0].changes[0].from").value("MEDIUM"))
                    .andExpect(jsonPath("$.content[0].changes[0].to").value("HIGH"))
                    .andExpect(jsonPath("$.content[0].changes[1].to").value("Tess"));
        }

        @Test
        void aBugStatusChangeReadsAsOldToNewWithTheReasonKept() throws Exception {
            UUID bug = create("/bug-reports", "{\"title\":\"Crash\",\"priority\":\"MEDIUM\"}");

            send(patch(url("/bug-reports/" + bug + "/status")),
                    "{\"status\":\"CLOSED\",\"reason\":\"fixed in 2.1\",\"resolution\":\"FIXED\"}");

            history(bug)
                    .andExpect(jsonPath("$.content[0].details").value("fixed in 2.1"))
                    .andExpect(jsonPath("$.content[0].changes[0].field").value("status"))
                    .andExpect(jsonPath("$.content[0].changes[0].from").value("NEW"))
                    .andExpect(jsonPath("$.content[0].changes[0].to").value("CLOSED"))
                    .andExpect(jsonPath("$.content[0].changes[1].to").value("FIXED"));
        }

        @Test
        void aTestCaseEditRecordsFieldsAndTheNewVersion() throws Exception {
            UUID testCase = create("/test-cases", "{\"title\":\"Login\",\"priority\":\"LOW\",\"status\":\"DRAFT\"}");

            send(put(url("/test-cases/" + testCase)), "{\"priority\":\"HIGH\",\"labels\":[\"ui\"]}");

            history(testCase)
                    .andExpect(jsonPath("$.content[0].changes[*].field", hasItem("priority")))
                    .andExpect(jsonPath("$.content[0].changes[?(@.field=='labels')].to", contains("ui")));
        }

        @Test
        void aRunStatusChangeRecordsTheOldStatus() throws Exception {
            UUID run = create("/test-runs", "{\"name\":\"Nightly\"}");

            send(put(url("/test-runs/" + run)), "{\"status\":\"IN_PROGRESS\"}");

            history(run)
                    .andExpect(jsonPath("$.content[0].changes[0].field").value("status"))
                    .andExpect(jsonPath("$.content[0].changes[0].from").value("PLANNED"))
                    .andExpect(jsonPath("$.content[0].changes[0].to").value("IN_PROGRESS"));
        }

        @Test
        void aPlanEditRecordsTheTargetDateAndGate() throws Exception {
            UUID plan = create("/test-plans", "{\"name\":\"2.1\"}");

            send(put(url("/test-plans/" + plan)),
                    "{\"name\":\"2.1\",\"targetDate\":\"2026-10-01\",\"gate\":{\"minPassRate\":95}}");

            history(plan)
                    .andExpect(jsonPath("$.content[0].changes[?(@.field=='targetDate')].to", contains("2026-10-01")))
                    .andExpect(jsonPath("$.content[0].changes[?(@.field=='gateMinPassRate')].to", contains("95")));
        }

        @Test
        void aSuiteRecordsItsCaseCount() throws Exception {
            UUID testCase = create("/test-cases", "{\"title\":\"Login\",\"priority\":\"LOW\",\"status\":\"DRAFT\"}");
            UUID suite = create("/test-suites", "{\"name\":\"Smoke\"}");

            send(put(url("/test-suites/" + suite)), "{\"name\":\"Smoke\",\"testCaseIds\":[\"" + testCase + "\"]}");

            history(suite)
                    .andExpect(jsonPath("$.content[0].changes[0].field").value("testCases"))
                    .andExpect(jsonPath("$.content[0].changes[0].from").value("0"))
                    .andExpect(jsonPath("$.content[0].changes[0].to").value("1"));
        }

        @Test
        void aSaveThatChangesNothingStillLogsWithoutChanges() throws Exception {
            UUID bug = create("/bug-reports", "{\"title\":\"Crash\",\"priority\":\"MEDIUM\"}");

            send(put(url("/bug-reports/" + bug)), "{\"title\":\"Crash\",\"priority\":\"MEDIUM\"}");

            history(bug)
                    .andExpect(jsonPath("$.content[0].action").value("UPDATED"))
                    .andExpect(jsonPath("$.content[0].changes").isEmpty());
        }
    }

    @Nested
    class Comments {

        @Test
        void aCommentNamesAndLinksTheCaseItIsOn() throws Exception {
            UUID testCase = create("/test-cases", "{\"title\":\"Login\",\"priority\":\"LOW\",\"status\":\"DRAFT\"}");

            send(post(url("/test-cases/" + testCase + "/comments")), "{\"content\":\"Flaky on Safari\"}");

            activity("entityType=COMMENT")
                    .andExpect(jsonPath("$.content[0].entityName").value("AUD-1 Login"))
                    .andExpect(jsonPath("$.content[0].parentEntityType").value("TEST_CASE"))
                    .andExpect(jsonPath("$.content[0].link.type").value("TEST_CASE"))
                    .andExpect(jsonPath("$.content[0].link.id").value(testCase.toString()));
        }

        @Test
        void theCaseHistoryIncludesItsComments() throws Exception {
            UUID testCase = create("/test-cases", "{\"title\":\"Login\",\"priority\":\"LOW\",\"status\":\"DRAFT\"}");

            send(post(url("/test-cases/" + testCase + "/comments")), "{\"content\":\"Flaky on Safari\"}");

            history(testCase).andExpect(jsonPath("$.content[*].entityType", contains("COMMENT", "TEST_CASE")));
        }
    }

    @Nested
    class Links {

        @Test
        void anEntryLinksToItsObjectUntilTheObjectIsDeleted() throws Exception {
            UUID bug = create("/bug-reports", "{\"title\":\"Crash\",\"priority\":\"MEDIUM\"}");
            history(bug).andExpect(jsonPath("$.content[0].link.id").value(bug.toString()));

            mockMvc.perform(delete(url("/bug-reports/" + bug)).with(user(tester))).andExpect(status().isNoContent());

            history(bug)
                    .andExpect(jsonPath("$.content[*].link", everyItem(is((Object) null))))
                    .andExpect(jsonPath("$.content[1].entityName").value("AUD-BUG-1 Crash"));
        }
    }

    @Nested
    class Filters {

        @Test
        void filtersByTypeActionAndUser() throws Exception {
            create("/test-cases", "{\"title\":\"Login\",\"priority\":\"LOW\",\"status\":\"DRAFT\"}");
            UUID bug = create("/bug-reports", "{\"title\":\"Crash\",\"priority\":\"MEDIUM\"}");
            send(put(url("/bug-reports/" + bug)), "{\"title\":\"Crash!\",\"priority\":\"MEDIUM\"}");

            activity("entityType=BUG_REPORT").andExpect(jsonPath("$.page.totalElements").value(2));
            activity("entityType=BUG_REPORT&action=UPDATED").andExpect(jsonPath("$.page.totalElements").value(1));
            activity("action=CREATED&entityType=TEST_CASE&entityType=BUG_REPORT")
                    .andExpect(jsonPath("$.page.totalElements").value(2));
            activity("userId=" + testerId).andExpect(jsonPath("$.page.totalElements").value(3));
            activity("userId=" + UUID.randomUUID()).andExpect(jsonPath("$.page.totalElements").value(0));
        }

        @Test
        void filtersByDateRange() throws Exception {
            create("/test-cases", "{\"title\":\"Login\",\"priority\":\"LOW\",\"status\":\"DRAFT\"}");
            Instant now = Instant.now();

            activity("from=" + now.minusSeconds(60) + "&to=" + now.plusSeconds(60))
                    .andExpect(jsonPath("$.page.totalElements").value(1));
            activity("to=" + now.minusSeconds(60)).andExpect(jsonPath("$.page.totalElements").value(0));
        }

        @Test
        void sortsOldestFirstOnRequest() throws Exception {
            create("/test-cases", "{\"title\":\"First\",\"priority\":\"LOW\",\"status\":\"DRAFT\"}");
            create("/test-cases", "{\"title\":\"Second\",\"priority\":\"LOW\",\"status\":\"DRAFT\"}");

            activity("sort=asc").andExpect(jsonPath("$.content[*].entityName", contains("First", "Second")));
            activity("sort=desc").andExpect(jsonPath("$.content[*].entityName", contains("Second", "First")));
        }

        @Test
        void aViewerReadsAndANonMemberDoesNot() throws Exception {
            mockMvc.perform(get(url("/activity")).with(user(viewer))).andExpect(status().isOk());
            mockMvc.perform(get(url("/activity")).with(user(outsider))).andExpect(status().isForbidden());
        }
    }

    @Nested
    class Export {

        @Test
        void exportsTheFilteredEntriesWithFormulasNeutralised() throws Exception {
            create("/test-cases", "{\"title\":\"=HYPERLINK(\\\"x\\\")\",\"priority\":\"LOW\",\"status\":\"DRAFT\"}");
            create("/test-plans", "{\"name\":\"2.1\"}");

            String csv = mockMvc.perform(get(url("/activity/export?entityType=TEST_CASE")).with(user(viewer)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(csv.lines()).hasSize(2);
            assertThat(csv).contains("'=HYPERLINK").doesNotContain("2.1");
        }
    }

    // ---- helpers --------------------------------------------------------------------------------

    private UUID create(String path, String body) throws Exception {
        String json = send(post(url(path)), body).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(json, "$.id"));
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                               String body) throws Exception {
        return mockMvc.perform(request.with(user(tester)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions history(UUID entityId) throws Exception {
        return activity("entityId=" + entityId);
    }

    private ResultActions activity(String query) throws Exception {
        return mockMvc.perform(get(url("/activity") + "?" + query).with(user(viewer))).andExpect(status().isOk());
    }

    private String url(String path) {
        return "/api/projects/" + projectId + path;
    }

    private User saveUser(String name) {
        User u = new User();
        u.setEmail(name.toLowerCase() + "-" + UUID.randomUUID() + "@test.local");
        u.setDisplayName(name);
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
}
