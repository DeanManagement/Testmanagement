package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.Screenshot;
import com.deanmanagement.testmanagement.project.internal.entity.StepResult;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestResult;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestRun;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestStep;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ScreenshotRepository;
import com.deanmanagement.testmanagement.project.internal.repository.StepResultRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestResultRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestRunRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestStepRepository;
import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.internal.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Uploaded media must never be served with an attacker-controlled content type: a
 * "screenshot" uploaded as text/html or image/svg+xml would otherwise execute script in
 * the app origin when viewed (stored XSS). Uploads are restricted to an image allowlist,
 * and downloads carry nosniff + a sandbox CSP; legacy rows with unsafe stored types are
 * downgraded to an octet-stream attachment.
 */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Transactional
class MediaContentTypeApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private TestCaseRepository testCaseRepository;
    @Autowired
    private TestStepRepository testStepRepository;
    @Autowired
    private TestRunRepository testRunRepository;
    @Autowired
    private TestResultRepository testResultRepository;
    @Autowired
    private StepResultRepository stepResultRepository;
    @Autowired
    private ScreenshotRepository screenshotRepository;

    private String admin;
    private UUID stepId;
    private UUID stepResultId;
    private StepResult stepResult;

    @BeforeEach
    void setUp() {
        User u = new User();
        u.setEmail("u-" + UUID.randomUUID() + "@test.local");
        u.setDisplayName("u");
        u.setPasswordHash("x");
        u.setSystemAdmin(true);
        admin = userRepository.save(u).getId().toString();

        Project project = new Project();
        project.setName("Media Types");
        project.setKey("MEDT");
        project = projectRepository.save(project);

        TestCase testCase = new TestCase();
        testCase.setProject(project);
        testCase.setTitle("tc");
        testCase.setKey("MEDT-1");
        testCase.setStatus(TestCaseStatus.ACTIVE);
        testCase.setPriority(Priority.LOW);
        testCase = testCaseRepository.save(testCase);

        TestStep step = new TestStep();
        step.setTestCase(testCase);
        step.setAction("do");
        step.setExpectedResult("done");
        step.setOrderIndex(0);
        stepId = testStepRepository.save(step).getId();

        TestRun run = new TestRun();
        run.setProject(project);
        run.setKey("MEDT-R1");
        run.setName("run");
        run.setStatus(TestRunStatus.IN_PROGRESS);
        run = testRunRepository.save(run);

        TestResult result = new TestResult();
        result.setTestRun(run);
        result.setTestCase(testCase);
        result.setStatus(TestResultStatus.PENDING);
        result = testResultRepository.save(result);

        stepResult = new StepResult();
        stepResult.setTestResult(result);
        stepResult.setTestStep(step);
        stepResult.setStatus(TestResultStatus.PENDING);
        stepResult = stepResultRepository.save(stepResult);
        stepResultId = stepResult.getId();
    }

    @Test
    void screenshotUpload_rejectsHtmlContentType() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "evil.html", "text/html", "<script>alert(1)</script>".getBytes());
        mockMvc.perform(multipart("/api/screenshots")
                        .file(file)
                        .param("stepResultId", stepResultId.toString())
                        .with(user(admin)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void screenshotUpload_rejectsSvgContentType() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "evil.svg", "image/svg+xml", "<svg onload=alert(1)/>".getBytes());
        mockMvc.perform(multipart("/api/screenshots")
                        .file(file)
                        .param("stepResultId", stepResultId.toString())
                        .with(user(admin)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void stepImageUpload_rejectsHtmlContentType() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "evil.html", "text/html", "<script>alert(1)</script>".getBytes());
        mockMvc.perform(multipart("/api/step-images")
                        .file(file)
                        .param("testStepId", stepId.toString())
                        .with(user(admin)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void screenshotUpload_acceptsPngAndServesItInlineWithHardeningHeaders() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "shot.png", "image/png", new byte[] {1, 2, 3});
        MvcResult created = mockMvc.perform(multipart("/api/screenshots")
                        .file(file)
                        .param("stepResultId", stepResultId.toString())
                        .with(user(admin)))
                .andExpect(status().isCreated())
                .andReturn();
        String id = created.getResponse().getContentAsString().replaceAll(".*\"([0-9a-f-]{36})\".*", "$1");

        MvcResult res = mockMvc.perform(get("/api/screenshots/{id}", id).with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "sandbox"))
                .andReturn();
        assertThat(res.getResponse().getContentType()).isEqualTo("image/png");
        assertThat(res.getResponse().getHeader("Content-Disposition")).startsWith("inline");
    }

    @Test
    void legacyUnsafeStoredContentType_isServedAsOctetStreamAttachment() throws Exception {
        // Simulates a row created before the upload allowlist existed.
        Screenshot legacy = new Screenshot();
        legacy.setStepResult(stepResult);
        legacy.setFileName("evil.html");
        legacy.setContentType("text/html");
        legacy.setData("<script>alert(1)</script>".getBytes());
        UUID id = screenshotRepository.save(legacy).getId();

        MvcResult res = mockMvc.perform(get("/api/screenshots/{id}", id).with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "sandbox"))
                .andReturn();
        assertThat(res.getResponse().getContentType()).isEqualTo("application/octet-stream");
        assertThat(res.getResponse().getHeader("Content-Disposition")).startsWith("attachment");
    }
}
