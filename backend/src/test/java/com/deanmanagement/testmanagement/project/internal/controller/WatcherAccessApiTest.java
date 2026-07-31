package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectMember;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.TestRun;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectMemberRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestRunRepository;
import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.internal.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PRD-021: watching an entity requires VIEWER access to its project. Without this check any
 * authenticated user could subscribe to arbitrary entity ids and receive notification payloads
 * from projects they are not a member of.
 */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Transactional
class WatcherAccessApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private ProjectMemberRepository projectMemberRepository;
    @Autowired
    private TestRunRepository testRunRepository;

    private String member;
    private String outsider;
    private UUID runId;

    @BeforeEach
    void setUp() {
        User memberUser = saveUser();
        User outsiderUser = saveUser();
        member = memberUser.getId().toString();
        outsider = outsiderUser.getId().toString();

        Project project = new Project();
        project.setName("Watch Project");
        project.setKey("WTCH");
        project = projectRepository.save(project);

        ProjectMember pm = new ProjectMember();
        pm.setUser(memberUser);
        pm.setProject(project);
        pm.setRole(ProjectRole.VIEWER);
        projectMemberRepository.save(pm);

        TestRun run = new TestRun();
        run.setProject(project);
        run.setKey("WTCH-R1");
        run.setName("run");
        run.setStatus(TestRunStatus.IN_PROGRESS);
        runId = testRunRepository.save(run).getId();
    }

    private User saveUser() {
        User u = new User();
        u.setEmail("u-" + UUID.randomUUID() + "@test.local");
        u.setDisplayName("u");
        u.setPasswordHash("x");
        u.setSystemAdmin(false);
        return userRepository.save(u);
    }

    private String watchBody() {
        return "{\"entityType\":\"TEST_RUN\",\"entityId\":\"" + runId + "\"}";
    }

    @Test
    void member_canWatch() throws Exception {
        mockMvc.perform(post("/api/watchers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(watchBody())
                        .with(user(member)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/watchers/check")
                        .param("entityType", "TEST_RUN")
                        .param("entityId", runId.toString())
                        .with(user(member)))
                .andExpect(jsonPath("$.watching").value(true));
    }

    @Test
    void nonMember_cannotWatch() throws Exception {
        mockMvc.perform(post("/api/watchers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(watchBody())
                        .with(user(outsider)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/watchers/check")
                        .param("entityType", "TEST_RUN")
                        .param("entityId", runId.toString())
                        .with(user(outsider)))
                .andExpect(jsonPath("$.watching").value(false));
    }

    @Test
    void unknownEntity_returns404() throws Exception {
        String body = "{\"entityType\":\"TEST_RUN\",\"entityId\":\"" + UUID.randomUUID() + "\"}";
        mockMvc.perform(post("/api/watchers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(user(member)))
                .andExpect(status().isNotFound());
    }
}
