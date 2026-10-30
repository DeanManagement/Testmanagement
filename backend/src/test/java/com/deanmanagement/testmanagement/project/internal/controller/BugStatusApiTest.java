package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectMember;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectMemberRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.service.BugStatusService;
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

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** TM47: a project's own bug statuses and the moves it allows between them. */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Transactional
class BugStatusApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectMemberRepository memberRepository;
    @Autowired private BugStatusService bugStatusService;

    private String admin;
    private String tester;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        Project project = new Project();
        project.setName("Workflow");
        project.setKey("WFL");
        project.setBugReportsEnabled(true);
        project = projectRepository.save(project);
        projectId = project.getId();
        bugStatusService.seedDefaults(project);
        admin = member(project, ProjectRole.ADMIN);
        tester = member(project, ProjectRole.TESTER);
    }

    private String member(Project project, ProjectRole role) {
        User u = new User();
        u.setEmail("u-" + UUID.randomUUID() + "@test.local");
        u.setDisplayName(role.name());
        u.setPasswordHash("x");
        u = userRepository.save(u);
        ProjectMember m = new ProjectMember();
        m.setUser(u);
        m.setProject(project);
        m.setRole(role);
        memberRepository.save(m);
        return u.getId().toString();
    }

    private String url(String path) {
        return "/api/projects/" + projectId + path;
    }

    private String statusId(String name) throws Exception {
        String body = mockMvc.perform(get(url("/bug-statuses")).with(user(tester)))
                .andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(body, "$[?(@.name == '" + name + "')].id");
        return ids.getFirst();
    }

    private String fileBug() throws Exception {
        String body = mockMvc.perform(post(url("/bug-reports")).with(user(tester))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Crash\",\"priority\":\"HIGH\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions moveTo(String bugId, String statusId, String extra) throws Exception {
        return mockMvc.perform(patch(url("/bug-reports/" + bugId + "/status")).with(user(tester))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"statusId\":\"" + statusId + "\",\"reason\":\"checked\"" + extra + "}"));
    }

    private void addStatus(String name, String category) throws Exception {
        mockMvc.perform(post(url("/bug-statuses")).with(user(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"color\":\"#0ea5e9\",\"category\":\"" + category + "\"}"))
                .andExpect(status().isCreated());
    }

    private ResultActions allow(String... pairs) throws Exception {
        StringBuilder body = new StringBuilder("{\"transitions\":[");
        for (int i = 0; i < pairs.length; i += 2) {
            body.append(i > 0 ? "," : "").append("{\"fromStatusId\":\"").append(statusId(pairs[i]))
                    .append("\",\"toStatusId\":\"").append(statusId(pairs[i + 1])).append("\"}");
        }
        return mockMvc.perform(put(url("/bug-statuses/transitions")).with(user(admin))
                .contentType(MediaType.APPLICATION_JSON).content(body.append("]}").toString()));
    }

    @Nested
    class Statuses {

        @Test
        void aNewBugStartsInTheFirstNewStatus() throws Exception {
            String bug = fileBug();

            mockMvc.perform(get(url("/bug-reports/" + bug)).with(user(tester)))
                    .andExpect(jsonPath("$.status").value("NEW"))
                    .andExpect(jsonPath("$.customStatusName").value("New"));
        }

        @Test
        void aProjectStatusCountsAsItsCategory() throws Exception {
            addStatus("Ready for retest", "RESOLVED");
            String bug = fileBug();

            moveTo(bug, statusId("Ready for retest"), "")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("RESOLVED"))
                    .andExpect(jsonPath("$.customStatusName").value("Ready for retest"));
        }

        @Test
        void aChangeByCategoryLandsOnItsFirstStatus() throws Exception {
            String bug = fileBug();

            mockMvc.perform(patch(url("/bug-reports/" + bug + "/status")).with(user(tester))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_PROGRESS\",\"reason\":\"on it\"}"))
                    .andExpect(jsonPath("$.customStatusName").value("In progress"));
        }

        @Test
        void theLastActiveStatusOfACategoryCannotBeArchived() throws Exception {
            mockMvc.perform(put(url("/bug-statuses/" + statusId("Closed"))).with(user(admin))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"archived\":true}"))
                    .andExpect(status().isConflict());
        }

        @Test
        void onlyAProjectAdminAddsStatuses() throws Exception {
            mockMvc.perform(post(url("/bug-statuses")).with(user(tester)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Mine\",\"color\":\"#0ea5e9\",\"category\":\"OPEN\"}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class Transitions {

        @Test
        void withoutATableAnyMoveIsAllowed() throws Exception {
            String bug = fileBug();

            moveTo(bug, statusId("Closed"), ",\"resolution\":\"FIXED\"").andExpect(status().isOk());
        }

        @Test
        void aMoveTheTableDoesNotAllowIsRefusedNamingTheAllowedOnes() throws Exception {
            allow("New", "Open", "New", "In progress");
            String bug = fileBug();

            moveTo(bug, statusId("Closed"), ",\"resolution\":\"FIXED\"")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", containsString("Allowed: Open, In progress")));
        }

        @Test
        void aBugFollowsTheAllowedPath() throws Exception {
            addStatus("Ready for retest", "RESOLVED");
            allow("New", "In progress", "In progress", "Ready for retest", "Ready for retest", "Closed");
            String bug = fileBug();

            moveTo(bug, statusId("In progress"), "").andExpect(status().isOk());
            moveTo(bug, statusId("Ready for retest"), "").andExpect(status().isOk());
            moveTo(bug, statusId("Closed"), ",\"resolution\":\"FIXED\"").andExpect(status().isOk())
                    .andExpect(jsonPath("$.customStatusName").value("Closed"));
        }

        @Test
        void eachStatusListsWhereItMayGo() throws Exception {
            allow("New", "Open");

            mockMvc.perform(get(url("/bug-statuses")).with(user(tester)))
                    .andExpect(jsonPath("$[?(@.name == 'New')].allowedTargetIds[*]", contains(statusId("Open"))))
                    .andExpect(jsonPath("$[?(@.name == 'Open')].allowedTargetIds[*]", hasSize(0)));
        }

        @Test
        void aBulkChangeIsRefusedWholeWhenOneMoveIsNotAllowed() throws Exception {
            allow("New", "Open");
            String bug = fileBug();

            mockMvc.perform(patch(url("/bug-reports/bulk")).with(user(tester)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"ids\":[\"" + bug + "\"],\"statusId\":\"" + statusId("Closed")
                                    + "\",\"resolution\":\"FIXED\",\"reason\":\"done\"}"))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(get(url("/bug-reports/" + bug)).with(user(tester)))
                    .andExpect(jsonPath("$.customStatusName").value("New"));
        }

        @Test
        void clearingTheTableAllowsAnyMoveAgain() throws Exception {
            allow("New", "Open");
            allow().andExpect(status().isOk());
            String bug = fileBug();

            moveTo(bug, statusId("Closed"), ",\"resolution\":\"FIXED\"").andExpect(status().isOk());
        }

        @Test
        void aTransitionToAnotherProjectsStatusIsRefused() throws Exception {
            mockMvc.perform(put(url("/bug-statuses/transitions")).with(user(admin)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"transitions\":[{\"fromStatusId\":\"" + statusId("New")
                                    + "\",\"toStatusId\":\"" + UUID.randomUUID() + "\"}]}"))
                    .andExpect(status().isBadRequest());
        }
    }
}
