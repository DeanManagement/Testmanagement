package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.entity.BugReport;
import com.deanmanagement.testmanagement.project.internal.entity.BugReportStatus;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectMember;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestRun;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import com.deanmanagement.testmanagement.project.internal.repository.BugReportRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectMemberRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Transactional
class SearchApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private ProjectMemberRepository projectMemberRepository;
    @Autowired
    private TestCaseRepository testCaseRepository;
    @Autowired
    private TestRunRepository testRunRepository;
    @Autowired
    private BugReportRepository bugReportRepository;

    private String member;
    private String admin;
    private UUID projectA;
    private UUID projectB;

    @BeforeEach
    void setUp() {
        member = newUser("member").toString();
        admin = newUser("admin").toString();

        projectA = newProject("Zebrafish Alpha", "APHA");
        projectB = newProject("Zebrafish Beta", "BETA");

        // member belongs to A only
        ProjectMember m = new ProjectMember();
        m.setProject(projectRepository.findById(projectA).orElseThrow());
        m.setUser(userRepository.findById(UUID.fromString(member)).orElseThrow());
        m.setRole(ProjectRole.ADMIN);
        projectMemberRepository.save(m);

        seedTestCase(projectA, "Zebrafish login flow", "APHA-1");
        seedTestCase(projectB, "Zebrafish payment flow", "BETA-1");
        seedTestRun(projectA, "Zebrafish smoke run", "APHA-Run-1");
        seedBugReport(projectA, "Zebrafish crash on submit");
    }

    private UUID newUser(String name) {
        User u = new User();
        u.setEmail(name + "-" + UUID.randomUUID() + "@test.local");
        u.setDisplayName(name);
        u.setPasswordHash("x");
        return userRepository.save(u).getId();
    }

    private UUID newProject(String name, String key) {
        Project p = new Project();
        p.setName(name);
        p.setKey(key);
        return projectRepository.save(p).getId();
    }

    private void seedTestCase(UUID projectId, String title, String key) {
        TestCase tc = new TestCase();
        tc.setProject(projectRepository.findById(projectId).orElseThrow());
        tc.setTitle(title);
        tc.setKey(key);
        tc.setStatus(TestCaseStatus.ACTIVE);
        tc.setPriority(Priority.MEDIUM);
        testCaseRepository.save(tc);
    }

    private void seedTestRun(UUID projectId, String name, String key) {
        TestRun run = new TestRun();
        run.setProject(projectRepository.findById(projectId).orElseThrow());
        run.setName(name);
        run.setKey(key);
        run.setStatus(TestRunStatus.IN_PROGRESS);
        testRunRepository.save(run);
    }

    private void seedBugReport(UUID projectId, String title) {
        BugReport bug = new BugReport();
        bug.setProject(projectRepository.findById(projectId).orElseThrow());
        bug.setTitle(title);
        bug.setPriority(Priority.HIGH);
        bug.setStatus(BugReportStatus.OPEN);
        bugReportRepository.save(bug);
    }

    @Test
    void member_seesOnlyTheirProjectsGroupedByType() throws Exception {
        mockMvc.perform(get("/api/search").param("q", "zebrafish").with(user(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.testCases.length()").value(1))
                .andExpect(jsonPath("$.testCases[0].title").value("Zebrafish login flow"))
                .andExpect(jsonPath("$.testCases[0].type").value("testCase"))
                .andExpect(jsonPath("$.testRuns.length()").value(1))
                .andExpect(jsonPath("$.bugReports.length()").value(1))
                .andExpect(jsonPath("$.projects.length()").value(1));
    }

    @Test
    void systemAdmin_seesAllProjects() throws Exception {
        mockMvc.perform(get("/api/search").param("q", "zebrafish").with(user(admin).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.testCases.length()").value(2));
    }

    @Test
    void typesFilterLimitsGroups() throws Exception {
        mockMvc.perform(get("/api/search").param("q", "zebrafish").param("types", "testCase").with(user(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.testCases.length()").value(1))
                .andExpect(jsonPath("$.testRuns.length()").value(0))
                .andExpect(jsonPath("$.bugReports.length()").value(0));
    }

    @Test
    void projectIdFilterScopesResults() throws Exception {
        // Admin restricting to project B sees only B's test case.
        mockMvc.perform(get("/api/search").param("q", "zebrafish").param("projectId", projectB.toString())
                        .param("types", "testCase").with(user(admin).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.testCases.length()").value(1))
                .andExpect(jsonPath("$.testCases[0].title").value("Zebrafish payment flow"));
    }

    @Test
    void shortQueryReturnsNothing() throws Exception {
        mockMvc.perform(get("/api/search").param("q", "z").with(user(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.testCases.length()").value(0))
                .andExpect(jsonPath("$.projects.length()").value(0));
    }
}
