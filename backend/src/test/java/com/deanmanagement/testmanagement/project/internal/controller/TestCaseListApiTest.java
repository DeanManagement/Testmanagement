package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseFolder;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseFolderRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.internal.repository.UserRepository;
import jakarta.persistence.EntityManager;
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
    @Autowired
    private TestCaseFolderRepository folderRepository;
    @Autowired
    private EntityManager entityManager;

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

    private TestCaseFolder folder(String name, TestCaseFolder parent) {
        TestCaseFolder f = new TestCaseFolder();
        f.setProject(projectRepository.findById(projectId).orElseThrow());
        f.setName(name);
        f.setParent(parent);
        return folderRepository.save(f);
    }

    private TestCase seedIn(String title, TestCaseFolder folder) {
        TestCase tc = seed(title, TestCaseStatus.ACTIVE, Priority.LOW);
        tc.setFolder(folder);
        return testCaseRepository.save(tc);
    }

    /** parent > child > grandchild, one case each, plus a sibling folder and a root case. */
    private TestCaseFolder seedFolderTree() {
        TestCaseFolder parent = folder("parent", null);
        TestCaseFolder child = folder("child", parent);
        TestCaseFolder grandchild = folder("grandchild", child);
        TestCaseFolder sibling = folder("sibling", null);
        seedIn("in parent", parent);
        seedIn("in child", child);
        seedIn("in grandchild", grandchild);
        seedIn("in sibling", sibling);
        seed("at root", TestCaseStatus.ACTIVE, Priority.LOW);
        // Drop the first-level cache so the folders' inverse-side testCases collections are
        // loaded fresh by the request, as they would be in production.
        entityManager.flush();
        entityManager.clear();
        return parent;
    }

    @Test
    void folderId_alone_isDirectContentsOnly() throws Exception {
        TestCaseFolder parent = seedFolderTree();

        mockMvc.perform(get("/api/projects/{p}/test-cases", projectId)
                        .param("folderId", parent.getId().toString()).with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("in parent"));
    }

    @Test
    void includeSubfolders_returnsWholeSubtree() throws Exception {
        TestCaseFolder parent = seedFolderTree();

        mockMvc.perform(get("/api/projects/{p}/test-cases", projectId)
                        .param("folderId", parent.getId().toString())
                        .param("includeSubfolders", "true")
                        .param("sort", "title,asc").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].title").value("in child"))
                .andExpect(jsonPath("$.content[1].title").value("in grandchild"))
                .andExpect(jsonPath("$.content[2].title").value("in parent"));
    }

    @Test
    void includeSubfolders_stillHonoursOtherFilters() throws Exception {
        TestCaseFolder parent = seedFolderTree();

        mockMvc.perform(get("/api/projects/{p}/test-cases", projectId)
                        .param("folderId", parent.getId().toString())
                        .param("includeSubfolders", "true")
                        .param("q", "grand").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("in grandchild"));
    }

    @Test
    void includeSubfolders_withoutFolderId_isIgnored() throws Exception {
        seedFolderTree();

        mockMvc.perform(get("/api/projects/{p}/test-cases", projectId)
                        .param("includeSubfolders", "true").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(5));
    }

    @Test
    void folderTree_reportsDirectAndRecursiveCounts() throws Exception {
        seedFolderTree();

        mockMvc.perform(get("/api/projects/{p}/test-case-folders", projectId).with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='parent')].testCaseCount").value(1))
                .andExpect(jsonPath("$[?(@.name=='parent')].totalTestCaseCount").value(3))
                .andExpect(jsonPath("$[?(@.name=='parent')].children[0].testCaseCount").value(1))
                .andExpect(jsonPath("$[?(@.name=='parent')].children[0].totalTestCaseCount").value(2))
                .andExpect(jsonPath("$[?(@.name=='sibling')].totalTestCaseCount").value(1));
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

    /** PRD-052: several labels narrow the list, they do not widen it. */
    @Test
    void severalLabelsMatchOnlyCasesCarryingAllOfThem() throws Exception {
        seed("both", TestCaseStatus.ACTIVE, Priority.LOW, "smoke", "ui");
        seed("smoke only", TestCaseStatus.ACTIVE, Priority.LOW, "smoke");
        seed("ui only", TestCaseStatus.ACTIVE, Priority.LOW, "ui");

        mockMvc.perform(get("/api/projects/{p}/test-cases", projectId)
                        .param("label", "smoke", "ui").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("both"));
    }

    @Test
    void labelsAreListedOnceEachSortedAndOnlyFromThisProject() throws Exception {
        seed("a", TestCaseStatus.ACTIVE, Priority.LOW, "ui", "smoke");
        seed("b", TestCaseStatus.ACTIVE, Priority.LOW, "smoke");
        Project other = new Project();
        other.setName("Other");
        other.setKey("OTHL");
        other = projectRepository.save(other);
        TestCase foreign = new TestCase();
        foreign.setProject(other);
        foreign.setTitle("foreign");
        foreign.setKey("OTHL-1");
        foreign.setStatus(TestCaseStatus.ACTIVE);
        foreign.setPriority(Priority.LOW);
        foreign.setLabels(new java.util.HashSet<>(Set.of("secret")));
        testCaseRepository.save(foreign);

        mockMvc.perform(get("/api/projects/{p}/test-cases/labels", projectId).with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0]").value("smoke"))
                .andExpect(jsonPath("$[1]").value("ui"));
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

    /** TES-BUG-21: a search for % or _ listed every case. */
    @Test
    void q_matchesWildcardCharactersLiterally() throws Exception {
        seed("Discount of 100% applies", TestCaseStatus.ACTIVE, Priority.LOW);
        seed("snake_case field names", TestCaseStatus.ACTIVE, Priority.LOW);
        seed("Payment flow", TestCaseStatus.ACTIVE, Priority.LOW);

        mockMvc.perform(get("/api/projects/{p}/test-cases", projectId).param("q", "%").with(user(admin)))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Discount of 100% applies"));
        mockMvc.perform(get("/api/projects/{p}/test-cases", projectId).param("q", "_").with(user(admin)))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("snake_case field names"));
    }
}
