package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.entity.BugReport;
import com.deanmanagement.testmanagement.project.internal.entity.BugReportStatus;
import com.deanmanagement.testmanagement.project.internal.entity.EntityWatcher;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectMember;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.WatchableEntityType;
import com.deanmanagement.testmanagement.project.internal.repository.BugReportRepository;
import com.deanmanagement.testmanagement.project.internal.repository.EntityWatcherRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectMemberRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.internal.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** TM44: the discussion on a bug report, its mentions and who hears about it. */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Transactional
class BugReportCommentApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectMemberRepository memberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private BugReportRepository bugReportRepository;
    @Autowired private EntityWatcherRepository watcherRepository;

    private Project project;
    private UUID bugId;
    private UUID tester;
    private UUID viewer;

    @BeforeEach
    void setUp() {
        project = project("BCM");
        tester = member(project, ProjectRole.TESTER);
        viewer = member(project, ProjectRole.VIEWER);
        bugId = bug(project);
    }

    private Project project(String key) {
        Project p = new Project();
        p.setName("Bug comments " + key);
        p.setKey(key);
        return projectRepository.save(p);
    }

    private UUID newUser() {
        User u = new User();
        u.setEmail("u-" + UUID.randomUUID() + "@test.local");
        u.setDisplayName("Person");
        u.setPasswordHash("x");
        return userRepository.save(u).getId();
    }

    private UUID member(Project target, ProjectRole role) {
        UUID id = newUser();
        ProjectMember m = new ProjectMember();
        m.setUser(userRepository.findById(id).orElseThrow());
        m.setProject(target);
        m.setRole(role);
        memberRepository.save(m);
        return id;
    }

    private UUID bug(Project target) {
        BugReport bug = new BugReport();
        bug.setKey(target.getKey() + "-BUG-" + UUID.randomUUID().toString().substring(0, 8));
        bug.setProject(target);
        bug.setTitle("Total is off by one");
        bug.setPriority(Priority.HIGH);
        bug.setStatus(BugReportStatus.OPEN);
        return bugReportRepository.save(bug).getId();
    }

    private void watch(UUID userId) {
        EntityWatcher watcher = new EntityWatcher();
        watcher.setUserId(userId);
        watcher.setEntityType(WatchableEntityType.BUG_REPORT);
        watcher.setEntityId(bugId);
        watcher.setCreatedAt(Instant.now());
        watcherRepository.save(watcher);
    }

    private ResultActions comment(UUID author, String body) throws Exception {
        return mockMvc.perform(post("/api/projects/" + project.getId() + "/bug-reports/" + bugId + "/comments")
                .with(user(author.toString())).with(csrf())
                .contentType("application/json").content(body));
    }

    private ResultActions notificationsOf(UUID userId) throws Exception {
        return mockMvc.perform(get("/api/me/notifications").with(user(userId.toString())));
    }

    @Test
    void aViewerCanDiscussABug() throws Exception {
        comment(viewer, "{\"content\":\"Is this release-blocking?\"}").andExpect(status().isCreated());

        mockMvc.perform(get("/api/projects/" + project.getId() + "/bug-reports/" + bugId + "/comments")
                        .with(user(tester.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].content").value("Is this release-blocking?"))
                .andExpect(jsonPath("$[0].entityType").value("BUG_REPORT"));
    }

    @Test
    void aMentionedMemberIsNotifiedOfTheMention() throws Exception {
        comment(tester, "{\"content\":\"@Person please check\",\"mentionedUserIds\":[\"" + viewer + "\"]}")
                .andExpect(status().isCreated());

        notificationsOf(viewer)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].action").value("MENTIONED"))
                .andExpect(jsonPath("$.content[0].entityType").value("BUG_REPORT"));
    }

    @Test
    void watchersAreNotifiedOfANewComment() throws Exception {
        watch(viewer);

        comment(tester, "{\"content\":\"Reproduced on staging\"}").andExpect(status().isCreated());

        notificationsOf(viewer)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].action").value("COMMENTED"));
    }

    @Test
    void aMentionedWatcherIsNotifiedOnce() throws Exception {
        watch(viewer);

        comment(tester, "{\"content\":\"@Person see above\",\"mentionedUserIds\":[\"" + viewer + "\"]}")
                .andExpect(status().isCreated());

        notificationsOf(viewer)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].action").value("MENTIONED"));
    }

    @Test
    void mentioningSomeoneOutsideTheProjectIsRefused() throws Exception {
        UUID outsider = newUser();

        comment(tester, "{\"content\":\"@Outsider\",\"mentionedUserIds\":[\"" + outsider + "\"]}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void aBugOfAnotherProjectIsNotFound() throws Exception {
        bugId = bug(project("BCX"));

        comment(tester, "{\"content\":\"Hello\"}").andExpect(status().isNotFound());
    }
}
