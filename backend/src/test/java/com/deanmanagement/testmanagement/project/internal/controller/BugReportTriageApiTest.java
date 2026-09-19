package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.entity.AuditAction;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectMember;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.repository.AuditEntryRepository;
import com.deanmanagement.testmanagement.project.internal.repository.BugReportRepository;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PRD-045: triage as a list operation. Keys, search and filters, the status and resolution rules,
 * bulk changes that are all or nothing, and the project's form template.
 */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Transactional
class BugReportTriageApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private ProjectMemberRepository projectMemberRepository;
    @Autowired
    private BugReportRepository bugReportRepository;
    @Autowired
    private AuditEntryRepository auditEntryRepository;

    private String admin;
    private String tester;
    private String viewer;
    private UUID testerId;
    private UUID outsiderId;
    private UUID projectId;
    private UUID otherProjectId;

    @BeforeEach
    void setUp() {
        User adminUser = saveUser("Ada");
        User testerUser = saveUser("Tess");
        User viewerUser = saveUser("Vic");
        admin = adminUser.getId().toString();
        tester = testerUser.getId().toString();
        viewer = viewerUser.getId().toString();
        testerId = testerUser.getId();
        outsiderId = saveUser("Otto").getId();

        Project project = saveProject("Triage", "TRI");
        projectId = project.getId();
        saveMember(adminUser, project, ProjectRole.ADMIN);
        saveMember(testerUser, project, ProjectRole.TESTER);
        saveMember(viewerUser, project, ProjectRole.VIEWER);

        Project other = saveProject("Other", "OTB");
        otherProjectId = other.getId();
        saveMember(testerUser, other, ProjectRole.TESTER);
    }

    @Nested
    class Keys {

        @Test
        void newBugsStartAsNewWithSequentialKeys() throws Exception {
            file(projectId, "First").andExpect(jsonPath("$.key").value("TRI-BUG-1"))
                    .andExpect(jsonPath("$.status").value("NEW"));
            file(projectId, "Second").andExpect(jsonPath("$.key").value("TRI-BUG-2"));
        }

        @Test
        void aBugCanBeFetchedByItsKey() throws Exception {
            file(projectId, "Crash on save");

            mockMvc.perform(get(url(projectId) + "/TRI-BUG-1").with(user(viewer)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("Crash on save"));
        }

        @Test
        void aKeyFromAnotherProjectIsNotFound() throws Exception {
            file(otherProjectId, "Elsewhere");

            mockMvc.perform(get(url(projectId) + "/OTB-BUG-1").with(user(tester)))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class Search {

        @Test
        void searchMatchesTheKeyAndTheTextFields() throws Exception {
            file(projectId, "Login fails");
            fileWith(projectId, "{\"title\":\"Other\",\"priority\":\"LOW\",\"actualBehavior\":\"stack trace in login\"}");
            file(projectId, "Unrelated");

            list("q=LOGIN").andExpect(jsonPath("$.page.totalElements").value(2));
            list("q=tri-bug-3").andExpect(jsonPath("$.content[0].title").value("Unrelated"));
        }

        @Test
        void filtersByStatusPriorityAndAssignee() throws Exception {
            UUID mine = id(fileWith(projectId, "{\"title\":\"Mine\",\"priority\":\"HIGH\",\"assigneeId\":\"" + testerId + "\"}"));
            fileWith(projectId, "{\"title\":\"Nobody's\",\"priority\":\"LOW\"}");

            list("assignee=me").andExpect(jsonPath("$.content[*].title", contains("Mine")));
            list("assignee=none").andExpect(jsonPath("$.content[*].title", contains("Nobody's")));
            list("assignee=none&assignee=me").andExpect(jsonPath("$.page.totalElements").value(2));
            list("priority=LOW").andExpect(jsonPath("$.content[*].title", contains("Nobody's")));
            changeStatus(mine, "{\"status\":\"OPEN\",\"reason\":\"confirmed\"}").andExpect(status().isOk());
            list("status=NEW").andExpect(jsonPath("$.content[*].title", contains("Nobody's")));
        }

        @Test
        void sortsByKeyNumericallyAndPages() throws Exception {
            for (int i = 1; i <= 11; i++) {
                file(projectId, "Bug " + i);
            }

            list("sort=key,desc&size=2")
                    .andExpect(jsonPath("$.content[*].key", contains("TRI-BUG-11", "TRI-BUG-10")))
                    .andExpect(jsonPath("$.page.totalElements").value(11));
        }

        @Test
        void anUnknownSortColumnIsABadRequest() throws Exception {
            list("sort=secretColumn").andExpect(status().isBadRequest());
        }
    }

    @Nested
    class StatusRules {

        @Test
        void closingWithoutAResolutionIsRefused() throws Exception {
            UUID bug = id(file(projectId, "Bug"));

            changeStatus(bug, "{\"status\":\"CLOSED\",\"reason\":\"done\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", containsString("resolution")));
        }

        @Test
        void aResolutionOnAnOpenStatusIsRefused() throws Exception {
            UUID bug = id(file(projectId, "Bug"));

            changeStatus(bug, "{\"status\":\"OPEN\",\"reason\":\"x\",\"resolution\":\"FIXED\"}")
                    .andExpect(status().isBadRequest());
        }

        @Test
        void aDuplicateNeedsAnotherBugOfTheSameProject() throws Exception {
            UUID bug = id(file(projectId, "Bug"));
            UUID foreign = id(file(otherProjectId, "Elsewhere"));

            changeStatus(bug, "{\"status\":\"CLOSED\",\"reason\":\"x\",\"resolution\":\"DUPLICATE\"}")
                    .andExpect(status().isBadRequest());
            changeStatus(bug, closeAsDuplicateOf(bug)).andExpect(status().isBadRequest());
            changeStatus(bug, closeAsDuplicateOf(foreign)).andExpect(status().isNotFound());
        }

        @Test
        void closingAsDuplicateLinksTheOriginal() throws Exception {
            UUID original = id(file(projectId, "Original"));
            UUID copy = id(file(projectId, "Copy"));

            changeStatus(copy, closeAsDuplicateOf(original))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resolution").value("DUPLICATE"))
                    .andExpect(jsonPath("$.duplicateOfKey").value("TRI-BUG-1"));
        }

        @Test
        void reopeningClearsTheResolutionAndTheDuplicateLink() throws Exception {
            UUID original = id(file(projectId, "Original"));
            UUID copy = id(file(projectId, "Copy"));
            changeStatus(copy, closeAsDuplicateOf(original));

            changeStatus(copy, "{\"status\":\"OPEN\",\"reason\":\"not the same after all\"}")
                    .andExpect(jsonPath("$.resolution").doesNotExist())
                    .andExpect(jsonPath("$.duplicateOfId").doesNotExist());
        }

        @Test
        void anEditDoesNotChangeTheStatus() throws Exception {
            UUID bug = id(file(projectId, "Bug"));

            mockMvc.perform(put(url(projectId) + "/" + bug).with(user(tester))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Renamed\",\"priority\":\"LOW\",\"status\":\"CLOSED\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("Renamed"))
                    .andExpect(jsonPath("$.status").value("NEW"));
        }
    }

    @Nested
    class Bulk {

        @Test
        void assignsReprioritisesAndMovesManyBugsWithOneAuditEntryEach() throws Exception {
            UUID a = id(file(projectId, "A"));
            UUID b = id(file(projectId, "B"));

            bulk(tester, "{\"ids\":[\"" + a + "\",\"" + b + "\"],\"assigneeId\":\"" + testerId
                    + "\",\"priority\":\"CRITICAL\",\"status\":\"OPEN\",\"reason\":\"triaged\"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.affected").value(2));

            list("assignee=me&priority=CRITICAL&status=OPEN").andExpect(jsonPath("$.page.totalElements").value(2));
            assertThat(auditEntryRepository.findAll().stream()
                    .filter(e -> e.getEntityType() == AuditEntityType.BUG_REPORT
                            && e.getAction() == AuditAction.STATUS_CHANGED)
                    .map(e -> e.getDetails() + " " + e.getChanges()))
                    .hasSize(2)
                    .allSatisfy(entry -> assertThat(entry).startsWith("triaged ")
                            .contains("\"field\":\"status\",\"from\":\"NEW\",\"to\":\"OPEN\""));
        }

        @Test
        void anIdFromAnotherProjectChangesNothing() throws Exception {
            UUID mine = id(file(projectId, "Mine"));
            UUID foreign = id(file(otherProjectId, "Foreign"));

            bulk(tester, "{\"ids\":[\"" + mine + "\",\"" + foreign + "\"],\"priority\":\"CRITICAL\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", containsString(foreign.toString())));
            list("priority=CRITICAL").andExpect(jsonPath("$.page.totalElements").value(0));
        }

        @Test
        void aNonMemberAssigneeChangesNothing() throws Exception {
            UUID bug = id(file(projectId, "Bug"));

            bulk(tester, "{\"ids\":[\"" + bug + "\"],\"assigneeId\":\"" + outsiderId + "\"}")
                    .andExpect(status().isBadRequest());
            list("assignee=none").andExpect(jsonPath("$.page.totalElements").value(1));
        }

        @Test
        void aBulkStatusChangeFollowsTheSingleChangeRules() throws Exception {
            UUID bug = id(file(projectId, "Bug"));

            bulk(tester, "{\"ids\":[\"" + bug + "\"],\"status\":\"CLOSED\",\"reason\":\"x\"}")
                    .andExpect(status().isBadRequest());
            bulk(tester, "{\"ids\":[\"" + bug + "\"],\"status\":\"OPEN\"}")
                    .andExpect(status().isBadRequest());
        }

        @Test
        void moreThanOneHundredIdsAreRefused() throws Exception {
            String ids = IntStream.range(0, 101).mapToObj(i -> "\"" + UUID.randomUUID() + "\"")
                    .collect(Collectors.joining(","));

            bulk(tester, "{\"ids\":[" + ids + "],\"priority\":\"LOW\"}").andExpect(status().isBadRequest());
        }

        @Test
        void aViewerCannotBulkChange() throws Exception {
            UUID bug = id(file(projectId, "Bug"));

            bulk(viewer, "{\"ids\":[\"" + bug + "\"],\"priority\":\"LOW\"}").andExpect(status().isForbidden());
        }

        @Test
        void bulkDeleteRemovesEveryBugOrNone() throws Exception {
            List<UUID> bugs = new ArrayList<>();
            bugs.add(id(file(projectId, "A")));
            bugs.add(id(file(projectId, "B")));

            mockMvc.perform(post(url(projectId) + "/bulk-delete").with(user(tester))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"ids\":[\"" + bugs.get(0) + "\",\"" + bugs.get(1) + "\"]}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.affected").value(2));
            assertThat(bugReportRepository.findAllById(bugs)).isEmpty();
        }
    }

    @Nested
    class Template {

        @Test
        void anAdminSetsTheTemplateAndTheProjectCarriesIt() throws Exception {
            template(admin, "{\"description\":\"What happened?\",\"stepsToReproduce\":\"1. \",\"environment\":\"staging\"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.bugTemplateDescription").value("What happened?"));

            mockMvc.perform(get("/api/projects/" + projectId).with(user(viewer)))
                    .andExpect(jsonPath("$.bugTemplateSteps").value("1."))
                    .andExpect(jsonPath("$.bugTemplateEnvironment").value("staging"));
        }

        @Test
        void aTesterCannotChangeTheTemplate() throws Exception {
            template(tester, "{\"description\":\"x\"}").andExpect(status().isForbidden());
        }
    }

    // ---- helpers --------------------------------------------------------------------------------

    private ResultActions file(UUID project, String title) throws Exception {
        return fileWith(project, "{\"title\":\"" + title + "\",\"priority\":\"MEDIUM\"}");
    }

    private ResultActions fileWith(UUID project, String body) throws Exception {
        return mockMvc.perform(post(url(project)).with(user(tester))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    private ResultActions list(String query) throws Exception {
        return mockMvc.perform(get(url(projectId) + "?" + query).with(user(tester)));
    }

    private ResultActions changeStatus(UUID bug, String body) throws Exception {
        return mockMvc.perform(patch(url(projectId) + "/" + bug + "/status").with(user(tester))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions bulk(String who, String body) throws Exception {
        return mockMvc.perform(patch(url(projectId) + "/bulk").with(user(who))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions template(String who, String body) throws Exception {
        return mockMvc.perform(put("/api/projects/" + projectId + "/settings/bug-template").with(user(who))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String closeAsDuplicateOf(UUID original) {
        return "{\"status\":\"CLOSED\",\"reason\":\"same crash\",\"resolution\":\"DUPLICATE\",\"duplicateOfId\":\""
                + original + "\"}";
    }

    private static UUID id(ResultActions created) throws Exception {
        return UUID.fromString(JsonPath.read(created.andReturn().getResponse().getContentAsString(), "$.id"));
    }

    private static String url(UUID project) {
        return "/api/projects/" + project + "/bug-reports";
    }

    private User saveUser(String name) {
        User u = new User();
        u.setEmail(name.toLowerCase() + "-" + UUID.randomUUID() + "@test.local");
        u.setDisplayName(name);
        u.setPasswordHash("x");
        u.setSystemAdmin(false);
        return userRepository.save(u);
    }

    private Project saveProject(String name, String key) {
        Project p = new Project();
        p.setName(name);
        p.setKey(key);
        p.setBugReportsEnabled(true);
        return projectRepository.save(p);
    }

    private void saveMember(User user, Project project, ProjectRole role) {
        ProjectMember pm = new ProjectMember();
        pm.setUser(user);
        pm.setProject(project);
        pm.setRole(role);
        projectMemberRepository.save(pm);
    }
}
