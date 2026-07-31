package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.Screenshot;
import com.deanmanagement.testmanagement.project.internal.entity.StepImage;
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
import com.deanmanagement.testmanagement.project.internal.repository.StepImageRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PRD-017: image responses are authorization-protected and must never be stored by shared
 * caches. Asserts Cache-Control is private (not public), revalidation still works (304),
 * and unauthorized callers cannot fetch the bytes.
 */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Transactional
class MediaCacheHeadersApiTest {

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
    private StepImageRepository stepImageRepository;
    @Autowired
    private TestRunRepository testRunRepository;
    @Autowired
    private TestResultRepository testResultRepository;
    @Autowired
    private StepResultRepository stepResultRepository;
    @Autowired
    private ScreenshotRepository screenshotRepository;

    private String admin;
    private String outsider;
    private UUID stepImageId;
    private UUID screenshotId;

    @BeforeEach
    void setUp() {
        admin = saveUser(true);
        outsider = saveUser(false);

        Project project = new Project();
        project.setName("Cache Headers");
        project.setKey("CACH");
        project = projectRepository.save(project);

        TestCase testCase = new TestCase();
        testCase.setProject(project);
        testCase.setTitle("tc");
        testCase.setKey("CACH-1");
        testCase.setStatus(TestCaseStatus.ACTIVE);
        testCase.setPriority(Priority.LOW);
        testCase = testCaseRepository.save(testCase);

        TestStep step = new TestStep();
        step.setTestCase(testCase);
        step.setAction("do");
        step.setExpectedResult("done");
        step.setOrderIndex(0);
        step = testStepRepository.save(step);

        StepImage image = new StepImage();
        image.setTestStep(step);
        image.setFileName("ref.png");
        image.setContentType("image/png");
        image.setData(new byte[] {1, 2, 3});
        stepImageId = stepImageRepository.save(image).getId();

        TestRun run = new TestRun();
        run.setProject(project);
        run.setKey("CACH-R1");
        run.setName("run");
        run.setStatus(TestRunStatus.IN_PROGRESS);
        run = testRunRepository.save(run);

        TestResult result = new TestResult();
        result.setTestRun(run);
        result.setTestCase(testCase);
        result.setStatus(TestResultStatus.PENDING);
        result = testResultRepository.save(result);

        StepResult stepResult = new StepResult();
        stepResult.setTestResult(result);
        stepResult.setTestStep(step);
        stepResult.setStatus(TestResultStatus.PENDING);
        stepResult = stepResultRepository.save(stepResult);

        Screenshot screenshot = new Screenshot();
        screenshot.setStepResult(stepResult);
        screenshot.setFileName("shot.png");
        screenshot.setContentType("image/png");
        screenshot.setData(new byte[] {4, 5, 6});
        screenshotId = screenshotRepository.save(screenshot).getId();
    }

    private String saveUser(boolean systemAdmin) {
        User u = new User();
        u.setEmail("u-" + UUID.randomUUID() + "@test.local");
        u.setDisplayName("u");
        u.setPasswordHash("x");
        u.setSystemAdmin(systemAdmin);
        return userRepository.save(u).getId().toString();
    }

    @Test
    void screenshot_cacheControlIsPrivateNotPublic() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/screenshots/{id}", screenshotId).with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andReturn();
        String cacheControl = res.getResponse().getHeader("Cache-Control");
        assertThat(cacheControl).contains("private").contains("immutable").doesNotContain("public");
    }

    @Test
    void stepImage_cacheControlIsPrivateNotPublic() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/step-images/{id}", stepImageId).with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andReturn();
        String cacheControl = res.getResponse().getHeader("Cache-Control");
        assertThat(cacheControl).contains("private").contains("immutable").doesNotContain("public");
    }

    @Test
    void screenshot_etagRevalidationStillReturns304() throws Exception {
        MvcResult first = mockMvc.perform(get("/api/screenshots/{id}", screenshotId).with(user(admin)))
                .andExpect(status().isOk())
                .andReturn();
        String etag = first.getResponse().getHeader("ETag");

        mockMvc.perform(get("/api/screenshots/{id}", screenshotId)
                        .header("If-None-Match", etag)
                        .with(user(admin)))
                .andExpect(status().isNotModified());
    }

    @Test
    void anonymous_cannotFetchImages() throws Exception {
        mockMvc.perform(get("/api/screenshots/{id}", screenshotId))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(get("/api/step-images/{id}", stepImageId))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void nonMember_cannotFetchImages() throws Exception {
        mockMvc.perform(get("/api/screenshots/{id}", screenshotId).with(user(outsider)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/step-images/{id}", stepImageId).with(user(outsider)))
                .andExpect(status().isForbidden());
    }
}
