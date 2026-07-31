package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.entity.AllureReport;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectMember;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.TestRun;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import com.deanmanagement.testmanagement.project.internal.repository.AllureReportRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PRD-018: Allure reports are untrusted HTML/JS. Viewing requires a short-lived,
 * single-report session token in the URL path (no JWT in URLs), and every /view response
 * carries a CSP sandbox so content is isolated even when opened directly.
 */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Transactional
class AllureReportViewApiTest {

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
    @Autowired
    private AllureReportRepository allureReportRepository;

    private UUID projectId;
    private String member;
    private String outsider;
    private static final String RUN_KEY = "ALLR-R1";

    @BeforeEach
    void setUp() throws Exception {
        User memberUser = saveUser();
        User outsiderUser = saveUser();
        member = memberUser.getId().toString();
        outsider = outsiderUser.getId().toString();

        Project project = new Project();
        project.setName("Allure Project");
        project.setKey("ALLR");
        project = projectRepository.save(project);
        projectId = project.getId();

        ProjectMember pm = new ProjectMember();
        pm.setUser(memberUser);
        pm.setProject(project);
        pm.setRole(ProjectRole.VIEWER);
        projectMemberRepository.save(pm);

        TestRun run = new TestRun();
        run.setProject(project);
        run.setKey(RUN_KEY);
        run.setName("run");
        run.setStatus(TestRunStatus.IN_PROGRESS);
        run = testRunRepository.save(run);

        AllureReport report = new AllureReport();
        report.setTestRun(run);
        report.setFileName("report.zip");
        report.setData(zipWithIndexHtml());
        allureReportRepository.save(report);
    }

    private User saveUser() {
        User u = new User();
        u.setEmail("u-" + UUID.randomUUID() + "@test.local");
        u.setDisplayName("u");
        u.setPasswordHash("x");
        u.setSystemAdmin(false);
        return userRepository.save(u);
    }

    private static byte[] zipWithIndexHtml() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            zos.putNextEntry(new ZipEntry("index.html"));
            zos.write("<html><body>allure</body></html>".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("widgets/summary.json"));
            zos.write("{\"total\":1}".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return bos.toByteArray();
    }

    private String sessionUrl() {
        return "/api/projects/" + projectId + "/test-runs/" + RUN_KEY + "/allure-report/session";
    }

    private String viewUrl(String token, String file) {
        return "/api/projects/" + projectId + "/test-runs/" + RUN_KEY + "/allure-report/view/"
                + token + "/" + file;
    }

    private String mintToken() throws Exception {
        String body = mockMvc.perform(post(sessionUrl()).with(user(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return body.replaceAll(".*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }

    @Test
    void member_canMintSessionAndViewReport_withCspSandbox() throws Exception {
        String token = mintToken();

        mockMvc.perform(get(viewUrl(token, "index.html")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", "sandbox allow-scripts allow-popups"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("allure")));

        // Relative data fetches inside the report stay under the token prefix and work.
        mockMvc.perform(get(viewUrl(token, "widgets/summary.json")))
                .andExpect(status().isOk());
    }

    @Test
    void nonMember_cannotMintSession() throws Exception {
        mockMvc.perform(post(sessionUrl()).with(user(outsider)))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidToken_isRejected() throws Exception {
        mockMvc.perform(get(viewUrl("not-a-real-token", "index.html")))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymous_cannotMintSession() throws Exception {
        mockMvc.perform(post(sessionUrl()))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void sandboxedIframe_opaqueOriginFetchesAreAllowed() throws Exception {
        // The sandboxed iframe has an opaque origin (Origin: null); the report's internal
        // JSON fetches must pass CORS on the view path.
        String token = mintToken();
        mockMvc.perform(get(viewUrl(token, "widgets/summary.json")).header("Origin", "null"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "*"));
    }

    @Test
    void jwtNoLongerAcceptedAsQueryParam() throws Exception {
        // The old ?token= flow must be dead: a view request without a valid path token fails
        // regardless of query parameters.
        mockMvc.perform(get(viewUrl("bogus", "index.html")).param("token", "some-jwt"))
                .andExpect(status().isForbidden());
    }
}
