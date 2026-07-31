package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.entity.AuditAction;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.EntityWatcher;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectMember;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.TestRun;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import com.deanmanagement.testmanagement.project.internal.entity.WatchableEntityType;
import com.deanmanagement.testmanagement.project.internal.repository.EntityWatcherRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectMemberRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestRunRepository;
import com.deanmanagement.testmanagement.project.internal.service.AuditService;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Transactional
class NotificationApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuditService auditService;
    @Autowired
    private EntityWatcherRepository watcherRepository;
    @Autowired
    private TestRunRepository testRunRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private ProjectMemberRepository projectMemberRepository;
    @Autowired
    private UserRepository userRepository;

    private UUID projectId;
    private UUID runId;
    private String actor;
    private String watcher;

    private UUID newUser(String name) {
        User u = new User();
        u.setEmail(name + "-" + UUID.randomUUID() + "@test.local");
        u.setDisplayName(name);
        u.setPasswordHash("x");
        return userRepository.save(u).getId();
    }

    @BeforeEach
    void setUp() {
        actor = newUser("actor").toString();
        watcher = newUser("watcher").toString();

        Project project = new Project();
        project.setName("Notify Project");
        project.setKey("NOTI");
        projectId = projectRepository.save(project).getId();

        // PRD-021: only project members receive notifications — make the watcher a member.
        ProjectMember membership = new ProjectMember();
        membership.setUser(userRepository.findById(UUID.fromString(watcher)).orElseThrow());
        membership.setProject(project);
        membership.setRole(ProjectRole.VIEWER);
        projectMemberRepository.save(membership);

        TestRun run = new TestRun();
        run.setProject(project);
        run.setName("Smoke run");
        run.setKey("NOTI-Run-1");
        run.setStatus(TestRunStatus.IN_PROGRESS);
        runId = testRunRepository.save(run).getId();

        EntityWatcher w = new EntityWatcher();
        w.setUserId(UUID.fromString(watcher));
        w.setEntityType(WatchableEntityType.TEST_RUN);
        w.setEntityId(runId);
        w.setCreatedAt(Instant.now());
        watcherRepository.save(w);
    }

    private void triggerAuditEvent() {
        auditService.log(projectId, UUID.fromString(actor), AuditAction.COMPLETED,
                AuditEntityType.TEST_RUN, runId, "Smoke run", null);
    }

    @Test
    void watcherReceivesNotification_actorDoesNot() throws Exception {
        triggerAuditEvent();

        mockMvc.perform(get("/api/me/notifications").with(user(watcher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].action").value("COMPLETED"))
                .andExpect(jsonPath("$.content[0].entityType").value("TEST_RUN"))
                .andExpect(jsonPath("$.content[0].read").value(false))
                .andExpect(jsonPath("$.content[0].actorName").value("actor"));

        mockMvc.perform(get("/api/me/notifications").with(user(actor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void unreadCountAndMarkRead() throws Exception {
        triggerAuditEvent();

        mockMvc.perform(get("/api/me/notifications/unread-count").with(user(watcher)))
                .andExpect(jsonPath("$.count").value(1));

        mockMvc.perform(post("/api/me/notifications/read-all").with(user(watcher)).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/me/notifications/unread-count").with(user(watcher)))
                .andExpect(jsonPath("$.count").value(0));

        mockMvc.perform(get("/api/me/notifications").param("unread", "true").with(user(watcher)))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void optOutSuppressesNotification() throws Exception {
        // Watcher opts out of in-app COMPLETED notifications.
        String prefs = "[{\"action\":\"COMPLETED\",\"inApp\":false,\"email\":false}]";
        mockMvc.perform(put("/api/me/notification-preferences").with(user(watcher)).with(csrf())
                        .contentType("application/json").content(prefs))
                .andExpect(status().isOk());

        triggerAuditEvent();

        mockMvc.perform(get("/api/me/notifications").with(user(watcher)))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void preferences_roundTrip() throws Exception {
        String prefs = "[{\"action\":\"UPDATED\",\"inApp\":true,\"email\":true}]";
        mockMvc.perform(put("/api/me/notification-preferences").with(user(watcher)).with(csrf())
                        .contentType("application/json").content(prefs))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].action").value("UPDATED"))
                .andExpect(jsonPath("$[0].email").value(true));

        mockMvc.perform(get("/api/me/notification-preferences").with(user(watcher)))
                .andExpect(jsonPath("$[0].action").value("UPDATED"));
    }
}
