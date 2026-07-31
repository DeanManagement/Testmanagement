package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
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

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack filtering/sorting/pagination coverage for the test-case list endpoint (PRD-002).
 * Uses a system admin to bypass RBAC so the focus stays on query behavior.
 */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Transactional
class TestCaseListApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private TestCaseRepository testCaseRepository;

    private UUID projectId;
    private String admin;
    private final AtomicInteger seq = new AtomicInteger(1);

    @BeforeEach
    void setUp() {
        User sysAdmin = new User();
        sysAdmin.setEmail("sa-" + UUID.randomUUID() + "@test.local");
        sysAdmin.setDisplayName("sa");
        sysAdmin.setPasswordHash("x");
        sysAdmin.setSystemAdmin(true);
        admin = userRepository.save(sysAdmin).getId().toString();

        Project project = new Project();
        project.setName("Filter Project");
        project.setKey("FILT");
        projectId = projectRepository.save(project).getId();
    }

    private TestCase seed(String title, TestCaseStatus status, Priority priority, String... labels) {
        Project project = projectRepository.findById(projectId).orElseThrow();
        TestCase tc = new TestCase();
        tc.setProject(project);
        tc.setTitle(title);
        tc.setKey("FILT-" + seq.getAndIncrement());
        tc.setStatus(status);
        tc.setPriority(priority);
        tc.setLabels(new java.util.HashSet<>(Set.of(labels)));
        return testCaseRepository.save(tc);
    }

    @Test
    void defaults_returnPageShape() throws Exception {
        seed("A", TestCaseStatus.ACTIVE, Priority.LOW);
        seed("B", TestCaseStatus.ACTIVE, Priority.LOW);
        seed("C", TestCaseStatus.ACTIVE, Priority.LOW);

        mockMvc.perform(get("/api/projects/{p}/test-cases", projectId).with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.size").value(50))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.content.length()").value(3));
    }

    @Test
    void q_filtersByTitleCaseInsensitive() throws Exception {
        seed("Login works", TestCaseStatus.ACTIVE, Priority.LOW);
        seed("Logout works", TestCaseStatus.ACTIVE, Priority.LOW);
        seed("Payment flow", TestCaseStatus.ACTIVE, Priority.LOW);

        mockMvc.perform(get("/api/projects/{p}/test-cases", projectId).param("q", "log").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    void status_repeatableParam() throws Exception {
        seed("a", TestCaseStatus.ACTIVE, Priority.LOW);
        seed("d", TestCaseStatus.DRAFT, Priority.LOW);
        seed("x", TestCaseStatus.DEPRECATED, Priority.LOW);

        mockMvc.perform(get("/api/projects/{p}/test-cases", projectId)
                        .param("status", "ACTIVE", "DRAFT").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    void priority_filter() throws Exception {
        seed("a", TestCaseStatus.ACTIVE, Priority.HIGH);
        seed("b", TestCaseStatus.ACTIVE, Priority.LOW);

        mockMvc.perform(get("/api/projects/{p}/test-cases", projectId)
                        .param("priority", "HIGH").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].priority").value("HIGH"));
    }

    @Test
    void label_filter() throws Exception {
        seed("a", TestCaseStatus.ACTIVE, Priority.LOW, "smoke", "ui");
        seed("b", TestCaseStatus.ACTIVE, Priority.LOW, "regression");

        mockMvc.perform(get("/api/projects/{p}/test-cases", projectId)
                        .param("label", "smoke").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void size_isCappedAt200() throws Exception {
        seed("a", TestCaseStatus.ACTIVE, Priority.LOW);

        mockMvc.perform(get("/api/projects/{p}/test-cases", projectId)
                        .param("size", "5000").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.size").value(200));
    }

    @Test
    void invalidEnum_returns400() throws Exception {
        mockMvc.perform(get("/api/projects/{p}/test-cases", projectId)
                        .param("status", "NOPE").with(user(admin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    void sort_byTitleAsc() throws Exception {
        seed("Zebra", TestCaseStatus.ACTIVE, Priority.LOW);
        seed("Apple", TestCaseStatus.ACTIVE, Priority.LOW);

        mockMvc.perform(get("/api/projects/{p}/test-cases", projectId)
                        .param("sort", "title,asc").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Apple"));
    }

    @Test
    void pagination_secondPage() throws Exception {
        seed("a", TestCaseStatus.ACTIVE, Priority.LOW);
        seed("b", TestCaseStatus.ACTIVE, Priority.LOW);
        seed("c", TestCaseStatus.ACTIVE, Priority.LOW);

        mockMvc.perform(get("/api/projects/{p}/test-cases", projectId)
                        .param("size", "2").param("page", "1").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page.totalPages").value(2));
    }
}
