package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.entity.AuditAction;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectMember;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.repository.AuditEntryRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectMemberRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.internal.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PRD-044: test case attachments. Role boundaries, project/case scoping, the headers that keep a
 * stored file from running script in the app origin, the limits, and the audit trail.
 */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {"app.attachments.max-per-case=2", "app.attachments.max-project-bytes=64"})
class AttachmentApiTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
    private static final byte[] PDF = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);

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
    private AuditEntryRepository auditEntryRepository;

    private String tester;
    private String viewer;
    private String outsider;
    private UUID projectId;
    private UUID caseId;
    private UUID otherCaseId;
    private UUID otherProjectId;
    private UUID otherProjectCaseId;

    @BeforeEach
    void setUp() {
        User testerUser = saveUser();
        User viewerUser = saveUser();
        tester = testerUser.getId().toString();
        viewer = viewerUser.getId().toString();
        outsider = saveUser().getId().toString();

        Project project = saveProject("Attach", "ATT");
        projectId = project.getId();
        saveMember(testerUser, project, ProjectRole.TESTER);
        saveMember(viewerUser, project, ProjectRole.VIEWER);
        caseId = saveCase(project, "ATT-1");
        otherCaseId = saveCase(project, "ATT-2");

        Project other = saveProject("Other", "OTH");
        otherProjectId = other.getId();
        saveMember(testerUser, other, ProjectRole.TESTER);
        otherProjectCaseId = saveCase(other, "OTH-1");
    }

    @Test
    void testerUploadsAndViewerListsAndDownloads() throws Exception {
        upload(tester, caseId, "spec.pdf", "application/pdf", PDF)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("spec.pdf"))
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.sizeBytes").value(PDF.length))
                .andExpect(jsonPath("$.sha256").isString());

        mockMvc.perform(get(url(projectId, caseId)).with(user(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fileName").value("spec.pdf"));
    }

    @Test
    void viewerCannotUploadOrDelete() throws Exception {
        UUID id = uploadOk(caseId, "spec.pdf", "application/pdf", PDF);

        upload(viewer, caseId, "spec.pdf", "application/pdf", PDF).andExpect(status().isForbidden());
        mockMvc.perform(delete(url(projectId, caseId) + "/" + id).with(user(viewer)))
                .andExpect(status().isForbidden());
    }

    @Test
    void testerDeletes() throws Exception {
        UUID id = uploadOk(caseId, "spec.pdf", "application/pdf", PDF);

        mockMvc.perform(delete(url(projectId, caseId) + "/" + id).with(user(tester)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(url(projectId, caseId) + "/" + id).with(user(tester)))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonMemberCannotList() throws Exception {
        mockMvc.perform(get(url(projectId, caseId)).with(user(outsider)))
                .andExpect(status().isForbidden());
    }

    @Test
    void attachmentIsNotReachableThroughAnotherCaseOrProject() throws Exception {
        UUID id = uploadOk(caseId, "spec.pdf", "application/pdf", PDF);

        mockMvc.perform(get(url(projectId, otherCaseId) + "/" + id).with(user(tester)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(url(otherProjectId, otherProjectCaseId) + "/" + id).with(user(tester)))
                .andExpect(status().isNotFound());
        // The case exists, but in the other project: the URL's project decides.
        mockMvc.perform(get(url(otherProjectId, caseId) + "/" + id).with(user(tester)))
                .andExpect(status().isNotFound());
    }

    @Test
    void pdfDownloadsAsAnAttachmentWithHardenedHeaders() throws Exception {
        UUID id = uploadOk(caseId, "spec.pdf", "application/pdf", PDF);

        MvcResult res = mockMvc.perform(get(url(projectId, caseId) + "/" + id).with(user(viewer)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "sandbox"))
                .andExpect(header().string("Content-Length", String.valueOf(PDF.length)))
                .andReturn();
        assertThat(res.getResponse().getHeader("Content-Disposition")).startsWith("attachment");
        assertThat(res.getResponse().getHeader("Cache-Control")).contains("private").doesNotContain("public");
        assertThat(res.getResponse().getContentType()).isEqualTo("application/octet-stream");
        assertThat(res.getResponse().getContentAsByteArray()).isEqualTo(PDF);
    }

    @Test
    void pngIsServedInlineAndRevalidatesWith304() throws Exception {
        UUID id = uploadOk(caseId, "shot.png", "image/png", PNG);

        MvcResult res = mockMvc.perform(get(url(projectId, caseId) + "/" + id).with(user(viewer)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(res.getResponse().getHeader("Content-Disposition")).startsWith("inline");
        assertThat(res.getResponse().getContentType()).isEqualTo("image/png");

        mockMvc.perform(get(url(projectId, caseId) + "/" + id)
                        .header("If-None-Match", res.getResponse().getHeader("ETag"))
                        .with(user(viewer)))
                .andExpect(status().isNotModified());
    }

    @Test
    void textIsNeverServedAsItsOwnType() throws Exception {
        byte[] xml = "<?xml version=\"1.0\"?><a/>".getBytes(StandardCharsets.UTF_8);
        UUID id = uploadOk(caseId, "data.xml", "application/xml", xml);

        MvcResult res = mockMvc.perform(get(url(projectId, caseId) + "/" + id).with(user(viewer)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(res.getResponse().getContentType()).isEqualTo("application/octet-stream");
        assertThat(res.getResponse().getHeader("Content-Disposition")).startsWith("attachment");
    }

    @Test
    void htmlDisguisedAsPdfIsRejected() throws Exception {
        upload(tester, caseId, "invoice.pdf", "application/pdf", "<html><script>x</script>".getBytes())
                .andExpect(status().isBadRequest());
    }

    @Test
    void svgIsRejected() throws Exception {
        upload(tester, caseId, "x.svg", "image/svg+xml", "<svg/>".getBytes())
                .andExpect(status().isBadRequest());
    }

    @Test
    void fileNameIsReducedToItsBaseName() throws Exception {
        upload(tester, caseId, "../../etc/pass\nwd.txt", "text/plain", "hi".getBytes())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("passwd.txt"));
    }

    @Test
    void uploadBeyondThePerCaseLimitIsAConflict() throws Exception {
        uploadOk(caseId, "a.txt", "text/plain", "a".getBytes());
        uploadOk(caseId, "b.txt", "text/plain", "b".getBytes());

        upload(tester, caseId, "c.txt", "text/plain", "c".getBytes()).andExpect(status().isConflict());
    }

    @Test
    void uploadBeyondTheProjectQuotaIsAConflict() throws Exception {
        uploadOk(caseId, "a.txt", "text/plain", "x".repeat(40).getBytes());

        upload(tester, otherCaseId, "b.txt", "text/plain", "y".repeat(40).getBytes())
                .andExpect(status().isConflict());
    }

    @Test
    void uploadAndDeleteAreAudited() throws Exception {
        UUID id = uploadOk(caseId, "spec.pdf", "application/pdf", PDF);
        mockMvc.perform(delete(url(projectId, caseId) + "/" + id).with(user(tester)))
                .andExpect(status().isNoContent());

        var entries = auditEntryRepository.findAll().stream()
                .filter(e -> e.getEntityType() == AuditEntityType.ATTACHMENT && id.equals(e.getEntityId()))
                .toList();
        assertThat(entries).extracting(e -> e.getAction())
                .containsExactlyInAnyOrder(AuditAction.CREATED, AuditAction.DELETED);
        assertThat(entries).allSatisfy(e -> {
            assertThat(e.getEntityName()).isEqualTo("spec.pdf");
            assertThat(e.getDetails()).contains("sha256");
        });
    }

    @Test
    void caseListAndDetailCarryAttachmentMetadata() throws Exception {
        uploadOk(caseId, "spec.pdf", "application/pdf", PDF);

        mockMvc.perform(get("/api/projects/{p}/test-cases", projectId).with(user(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.key=='ATT-1')].attachments.length()").value(1))
                .andExpect(jsonPath("$.content[?(@.key=='ATT-2')].attachments.length()").value(0));
        mockMvc.perform(get("/api/projects/{p}/test-cases/{id}", projectId, caseId).with(user(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attachments[0].fileName").value("spec.pdf"));
    }

    @Test
    void jsonExportCarriesMetadataAndImportSaysTheFilesDidNotComeAcross() throws Exception {
        uploadOk(caseId, "spec.pdf", "application/pdf", PDF);

        String json = mockMvc.perform(get("/api/projects/{p}/test-cases/export", projectId)
                        .param("format", "json").with(user(tester)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.key=='ATT-1')].attachments[0].fileName").value("spec.pdf"))
                .andExpect(jsonPath("$[?(@.key=='ATT-1')].attachments[0].sha256").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat(json).doesNotContain("\"data\"");

        mockMvc.perform(multipart("/api/projects/{p}/test-cases/import", otherProjectId)
                        .file(new MockMultipartFile("file", "cases.json", "application/json", json.getBytes()))
                        .param("dryRun", "true").with(user(tester)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(2))
                .andExpect(jsonPath("$.warnings[0].message").value("1 attachment(s) not imported"));
    }

    // ---- helpers --------------------------------------------------------------------------------

    private ResultActions upload(String who, UUID testCaseId, String name, String type, byte[] data) throws Exception {
        return mockMvc.perform(multipart(url(projectId, testCaseId))
                .file(new MockMultipartFile("file", name, type, data))
                .with(user(who)));
    }

    private UUID uploadOk(UUID testCaseId, String name, String type, byte[] data) throws Exception {
        String body = upload(tester, testCaseId, name, type, data)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private static String url(UUID project, UUID testCase) {
        return "/api/projects/" + project + "/test-cases/" + testCase + "/attachments";
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
        return projectRepository.save(p);
    }

    private void saveMember(User user, Project project, ProjectRole role) {
        ProjectMember pm = new ProjectMember();
        pm.setUser(user);
        pm.setProject(project);
        pm.setRole(role);
        projectMemberRepository.save(pm);
    }

    private UUID saveCase(Project project, String key) {
        TestCase tc = new TestCase();
        tc.setProject(project);
        tc.setKey(key);
        tc.setTitle(key);
        tc.setPriority(Priority.MEDIUM);
        tc.setStatus(TestCaseStatus.ACTIVE);
        return testCaseRepository.save(tc).getId();
    }
}
