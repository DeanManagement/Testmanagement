package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectMember;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectMemberRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP wiring of the PRD-033 review endpoints: status codes, role guards and settings. */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Transactional
class TestCaseReviewApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectMemberRepository memberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TestCaseRepository testCaseRepository;

    private Project project;
    private String admin;
    private String tester;
    private String viewer;
    private TestCase inReview;

    @BeforeEach
    void setUp() {
        project = new Project();
        project.setName("Reviewed API");
        project.setKey("RAPI");
        project = projectRepository.save(project);
        admin = member(ProjectRole.ADMIN);
        tester = member(ProjectRole.TESTER);
        viewer = member(ProjectRole.VIEWER);

        inReview = new TestCase();
        inReview.setProject(project);
        inReview.setKey("RAPI-1");
        inReview.setTitle("Login");
        inReview.setPriority(Priority.MEDIUM);
        inReview.setStatus(TestCaseStatus.IN_REVIEW);
        inReview = testCaseRepository.save(inReview);
    }

    private String member(ProjectRole role) {
        User u = new User();
        u.setEmail("u-" + UUID.randomUUID() + "@test.local");
        u.setDisplayName("u");
        u.setPasswordHash("x");
        u = userRepository.save(u);
        ProjectMember m = new ProjectMember();
        m.setUser(u);
        m.setProject(project);
        m.setRole(role);
        memberRepository.save(m);
        return u.getId().toString();
    }

    private String caseUrl(String action) {
        return "/api/projects/" + project.getId() + "/test-cases/" + inReview.getId() + "/" + action;
    }

    private void requireReview() throws Exception {
        mockMvc.perform(put("/api/projects/" + project.getId() + "/settings/review")
                        .with(user(admin)).with(csrf())
                        .contentType("application/json")
                        .content("{\"reviewRequired\":true,\"reviewerMinRole\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewRequired").value(true))
                .andExpect(jsonPath("$.reviewerMinRole").value("ADMIN"));
    }

    @Test
    void anAdminApprovesAndTheResponseCarriesTheApproval() throws Exception {
        requireReview();

        mockMvc.perform(post(caseUrl("approve")).with(user(admin)).with(csrf())
                        .contentType("application/json").content("{\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.approvedVersion").value(1))
                .andExpect(jsonPath("$.approvedBy").value(admin));
    }

    @Test
    void aStaleVersionIs409() throws Exception {
        requireReview();

        mockMvc.perform(post(caseUrl("approve")).with(user(admin)).with(csrf())
                        .contentType("application/json").content("{\"version\":7}"))
                .andExpect(status().isConflict());
    }

    @Test
    void aTesterBelowTheReviewerRoleIs403() throws Exception {
        requireReview();

        mockMvc.perform(post(caseUrl("approve")).with(user(tester)).with(csrf())
                        .contentType("application/json").content("{\"version\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aViewerCannotEvenReachApprove() throws Exception {
        mockMvc.perform(post(caseUrl("approve")).with(user(viewer)).with(csrf())
                        .contentType("application/json").content("{\"version\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlyAnAdminChangesTheReviewSetting() throws Exception {
        mockMvc.perform(put("/api/projects/" + project.getId() + "/settings/review")
                        .with(user(tester)).with(csrf())
                        .contentType("application/json").content("{\"reviewRequired\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void activatingThroughAPlainUpdateIs400UnderReview() throws Exception {
        requireReview();

        mockMvc.perform(put("/api/projects/" + project.getId() + "/test-cases/" + inReview.getId())
                        .with(user(admin)).with(csrf())
                        .contentType("application/json").content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void capabilitiesReportWhatTheCallerMayDo() throws Exception {
        requireReview();

        mockMvc.perform(get(caseUrl("review-capabilities")).with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewRequired").value(true))
                .andExpect(jsonPath("$.canApprove").value(true));
    }
}
