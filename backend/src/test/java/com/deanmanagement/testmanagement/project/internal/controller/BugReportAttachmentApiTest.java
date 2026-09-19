package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.entity.AuditEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectMember;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.Screenshot;
import com.deanmanagement.testmanagement.project.internal.entity.StepResult;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestResult;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestRun;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import com.deanmanagement.testmanagement.project.internal.repository.AttachmentRepository;
import com.deanmanagement.testmanagement.project.internal.repository.AuditEntryRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectMemberRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ScreenshotRepository;
import com.deanmanagement.testmanagement.project.internal.repository.StepResultRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestResultRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestRunRepository;
import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.internal.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PRD-051: files on a bug report. The same storage, headers and limits as test case attachments,
 * scoped to the bug and its project, plus the step screenshots copied when a bug is filed from a
 * failed result.
 */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {"app.attachments.max-per-owner=2", "app.attachments.max-project-bytes=64"})
class BugReportAttachmentApiTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
    private static final byte[] PDF = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EntityManager entityManager;
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
    private TestResultRepository testResultRepository;
    @Autowired
    private StepResultRepository stepResultRepository;
    @Autowired
    private ScreenshotRepository screenshotRepository;
    @Autowired
    private AttachmentRepository attachmentRepository;
    @Autowired
    private AuditEntryRepository auditEntryRepository;

    private String tester;
    private String viewer;
    private Project project;
    private Project otherProject;
    private UUID bugId;
    private String bugKey;
    private UUID otherBugId;

    @BeforeEach
    void setUp() throws Exception {
        User testerUser = saveUser();
        User viewerUser = saveUser();
        tester = testerUser.getId().toString();
        viewer = viewerUser.getId().toString();

        project = saveProject("Bugs", "BGA");
        saveMember(testerUser, project, ProjectRole.TESTER);
        saveMember(viewerUser, project, ProjectRole.VIEWER);
        otherProject = saveProject("Other", "BGO");
        saveMember(testerUser, otherProject, ProjectRole.TESTER);

        bugId = fileBug(project, "Checkout fails", null);
        bugKey = "BGA-BUG-1";
        otherBugId = fileBug(project, "Login fails", null);
    }

    @Test
    void testerUploadsByKeyAndViewerListsAndSeesTheImageInline() throws Exception {
        UUID id = uploadOk(bugKey, "shot.png", "image/png", PNG);

        mockMvc.perform(get(url(project, bugId)).with(user(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fileName").value("shot.png"))
                .andExpect(jsonPath("$[0].bugReportId").value(bugId.toString()))
                .andExpect(jsonPath("$[0].testCaseId").doesNotExist());
        MvcResult res = mockMvc.perform(get(url(project, bugId) + "/" + id).with(user(viewer)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "sandbox"))
                .andReturn();
        assertThat(res.getResponse().getHeader("Content-Disposition")).startsWith("inline");
        assertThat(res.getResponse().getHeader("Cache-Control")).contains("private");
        assertThat(res.getResponse().getContentAsByteArray()).isEqualTo(PNG);
    }

    @Test
    void aPdfDownloadsAsAnAttachment() throws Exception {
        UUID id = uploadOk(bugId.toString(), "log.pdf", "application/pdf", PDF);

        MvcResult res = mockMvc.perform(get(url(project, bugId) + "/" + id).with(user(viewer)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(res.getResponse().getHeader("Content-Disposition")).startsWith("attachment");
        assertThat(res.getResponse().getContentType()).isEqualTo("application/octet-stream");
    }

    @Test
    void viewerCannotUploadOrDelete() throws Exception {
        UUID id = uploadOk(bugId.toString(), "shot.png", "image/png", PNG);

        upload(viewer, bugId.toString(), "shot.png", "image/png", PNG).andExpect(status().isForbidden());
        mockMvc.perform(delete(url(project, bugId) + "/" + id).with(user(viewer)))
                .andExpect(status().isForbidden());
    }

    @Test
    void testerDeletes() throws Exception {
        UUID id = uploadOk(bugId.toString(), "shot.png", "image/png", PNG);

        mockMvc.perform(delete(url(project, bugId) + "/" + id).with(user(tester)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(url(project, bugId) + "/" + id).with(user(tester)))
                .andExpect(status().isNotFound());
    }

    @Test
    void anAttachmentIsNotReachableThroughAnotherBugOrProject() throws Exception {
        UUID id = uploadOk(bugId.toString(), "shot.png", "image/png", PNG);

        mockMvc.perform(get(url(project, otherBugId) + "/" + id).with(user(tester)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(url(otherProject, bugId) + "/" + id).with(user(tester)))
                .andExpect(status().isNotFound());
    }

    @Test
    void svgIsRejected() throws Exception {
        upload(tester, bugId.toString(), "x.svg", "image/svg+xml", "<svg/>".getBytes())
                .andExpect(status().isBadRequest());
    }

    @Test
    void theLimitPerOwnerAppliesToABug() throws Exception {
        uploadOk(bugId.toString(), "a.txt", "text/plain", "a".getBytes());
        uploadOk(bugId.toString(), "b.txt", "text/plain", "b".getBytes());

        upload(tester, bugId.toString(), "c.txt", "text/plain", "c".getBytes()).andExpect(status().isConflict());
    }

    @Test
    void theProjectQuotaCountsCaseAndBugAttachmentsTogether() throws Exception {
        UUID caseId = saveCase(project, "BGA-1");
        mockMvc.perform(multipart("/api/projects/{p}/test-cases/{c}/attachments", project.getId(), caseId)
                        .file(new MockMultipartFile("file", "a.txt", "text/plain", "x".repeat(40).getBytes()))
                        .with(user(tester)))
                .andExpect(status().isCreated());

        upload(tester, bugId.toString(), "b.txt", "text/plain", "y".repeat(40).getBytes())
                .andExpect(status().isConflict());
    }

    @Test
    void uploadIsAuditedAgainstTheBug() throws Exception {
        UUID id = uploadOk(bugId.toString(), "shot.png", "image/png", PNG);

        var entry = auditEntryRepository.findAll().stream()
                .filter(e -> e.getEntityType() == AuditEntityType.ATTACHMENT && id.equals(e.getEntityId()))
                .findFirst().orElseThrow();
        assertThat(entry.getDetails()).startsWith(bugKey).contains("sha256");
    }

    @Test
    void deletingTheBugDeletesItsAttachments() throws Exception {
        uploadOk(bugId.toString(), "shot.png", "image/png", PNG);
        // Separate requests in production: the delete never sees the upload's managed attachment.
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(delete("/api/projects/{p}/bug-reports/{id}", project.getId(), bugId).with(user(tester)))
                .andExpect(status().isNoContent());
        entityManager.flush();
        entityManager.clear();

        assertThat(attachmentRepository.countByBugReportId(bugId)).isZero();
    }

    @Test
    void attachmentsNeedBugReportsEnabled() throws Exception {
        project.setBugReportsEnabled(false);
        projectRepository.save(project);

        mockMvc.perform(get(url(project, bugId)).with(user(tester))).andExpect(status().isForbidden());
    }

    @Test
    void reportingFromAResultCopiesItsStepScreenshotsUpToTheLimit() throws Exception {
        TestResult result = resultWithScreenshots(3);

        UUID filed = fileBug(project, "Payment fails", result.getId());

        mockMvc.perform(get(url(project, filed)).with(user(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].fileName").value("step-1-shot.png"))
                .andExpect(jsonPath("$[1].fileName").value("step-2-shot.png"));
    }

    @Test
    void aCopiedScreenshotDoesNotChangeWhenTheStepGetsANewOne() throws Exception {
        TestResult result = resultWithScreenshots(1);
        UUID filed = fileBug(project, "Payment fails", result.getId());
        String copied = sha(filed);

        Screenshot original = screenshotRepository.findByTestResultId(result.getId()).getFirst();
        original.setData(new byte[]{(byte) 0x89, 'P', 'N', 'G', 1, 2, 3, 4});
        screenshotRepository.saveAndFlush(original);

        assertThat(sha(filed)).isEqualTo(copied);
    }

    // ---- helpers --------------------------------------------------------------------------------

    private UUID fileBug(Project owner, String title, UUID testResultId) throws Exception {
        String body = "{\"title\":\"" + title + "\",\"priority\":\"HIGH\""
                + (testResultId == null ? "" : ",\"testResultId\":\"" + testResultId + "\"") + "}";
        String response = mockMvc.perform(post("/api/projects/{p}/bug-reports", owner.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(body).with(user(tester)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.id"));
    }

    private String sha(UUID bug) throws Exception {
        String body = mockMvc.perform(get(url(project, bug)).with(user(viewer)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$[0].sha256");
    }

    private TestResult resultWithScreenshots(int count) {
        TestCase testCase = testCaseRepository.findById(saveCase(project, "BGA-9")).orElseThrow();
        TestRun run = new TestRun();
        run.setProject(project);
        run.setKey("BGA-R1");
        run.setName("run");
        run.setStatus(TestRunStatus.IN_PROGRESS);
        run = testRunRepository.save(run);
        TestResult result = new TestResult();
        result.setTestRun(run);
        result.setTestCase(testCase);
        result.setStatus(TestResultStatus.FAILED, null);
        result = testResultRepository.save(result);
        for (int i = 0; i < count; i++) {
            StepResult step = new StepResult();
            step.setTestResult(result);
            step.setPosition(i);
            step.setStatus(TestResultStatus.FAILED);
            step = stepResultRepository.save(step);
            Screenshot shot = new Screenshot();
            shot.setStepResult(step);
            shot.setFileName("shot.png");
            shot.setContentType("image/png");
            shot.setData(PNG);
            screenshotRepository.save(shot);
        }
        return result;
    }

    private ResultActions upload(String who, String bug, String name, String type, byte[] data) throws Exception {
        return mockMvc.perform(multipart("/api/projects/" + project.getId() + "/bug-reports/" + bug + "/attachments")
                .file(new MockMultipartFile("file", name, type, data))
                .with(user(who)));
    }

    private UUID uploadOk(String bug, String name, String type, byte[] data) throws Exception {
        String body = upload(tester, bug, name, type, data)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private static String url(Project owner, UUID bug) {
        return "/api/projects/" + owner.getId() + "/bug-reports/" + bug + "/attachments";
    }

    private User saveUser() {
        User u = new User();
        u.setEmail("u-" + UUID.randomUUID() + "@test.local");
        u.setDisplayName("u");
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

    private void saveMember(User member, Project owner, ProjectRole role) {
        ProjectMember pm = new ProjectMember();
        pm.setUser(member);
        pm.setProject(owner);
        pm.setRole(role);
        projectMemberRepository.save(pm);
    }

    private UUID saveCase(Project owner, String key) {
        TestCase tc = new TestCase();
        tc.setProject(owner);
        tc.setKey(key);
        tc.setTitle(key);
        tc.setPriority(Priority.MEDIUM);
        tc.setStatus(TestCaseStatus.ACTIVE);
        return testCaseRepository.save(tc).getId();
    }
}
