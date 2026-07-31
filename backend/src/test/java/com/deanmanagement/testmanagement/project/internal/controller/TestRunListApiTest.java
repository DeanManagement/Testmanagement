package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestResult;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestRun;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestResultRepository;
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

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The test-run list endpoints return summaries with aggregate result counts instead of the
 * full results collection — the counts must match and the heavy collection must be absent.
 */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Transactional
class TestRunListApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private TestCaseRepository testCaseRepository;
    @Autowired
    private TestRunRepository testRunRepository;
    @Autowired
    private TestResultRepository testResultRepository;

    private String admin;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        User u = new User();
        u.setEmail("u-" + UUID.randomUUID() + "@test.local");
        u.setDisplayName("u");
        u.setPasswordHash("x");
        u.setSystemAdmin(true);
        admin = userRepository.save(u).getId().toString();

        Project project = new Project();
        project.setName("Run List");
        project.setKey("RNLS");
        project = projectRepository.save(project);
        projectId = project.getId();

        TestCase testCase = new TestCase();
        testCase.setProject(project);
        testCase.setTitle("tc");
        testCase.setKey("RNLS-1");
        testCase.setStatus(TestCaseStatus.ACTIVE);
        testCase.setPriority(Priority.LOW);
        testCase = testCaseRepository.save(testCase);

        TestRun run = new TestRun();
        run.setProject(project);
        run.setKey("RNLS-R1");
        run.setName("run");
        run.setStatus(TestRunStatus.IN_PROGRESS);
        run = testRunRepository.save(run);

        saveResult(run, testCase, TestResultStatus.PASSED);
        saveResult(run, testCase, TestResultStatus.PASSED);
        saveResult(run, testCase, TestResultStatus.FAILED);
        saveResult(run, testCase, TestResultStatus.PENDING);
    }

    private void saveResult(TestRun run, TestCase testCase, TestResultStatus status) {
        TestResult result = new TestResult();
        result.setTestRun(run);
        result.setTestCase(testCase);
        result.setStatus(status);
        testResultRepository.save(result);
    }

    @Test
    void listReturnsResultCountsInsteadOfResultsCollection() throws Exception {
        mockMvc.perform(get("/api/projects/{projectId}/test-runs", projectId).with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].key").value("RNLS-R1"))
                .andExpect(jsonPath("$.content[0].total").value(4))
                .andExpect(jsonPath("$.content[0].passed").value(2))
                .andExpect(jsonPath("$.content[0].failed").value(1))
                .andExpect(jsonPath("$.content[0].pending").value(1))
                .andExpect(jsonPath("$.content[0].blocked").value(0))
                .andExpect(jsonPath("$.content[0].results").doesNotExist());
    }

    @Test
    void runWithoutResultsReportsZeroCounts() throws Exception {
        TestRun empty = new TestRun();
        empty.setProject(projectRepository.findById(projectId).orElseThrow());
        empty.setKey("RNLS-R2");
        empty.setName("empty run");
        empty.setStatus(TestRunStatus.PLANNED);
        testRunRepository.save(empty);

        mockMvc.perform(get("/api/projects/{projectId}/test-runs", projectId)
                        .param("q", "empty")
                        .with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].key").value("RNLS-R2"))
                .andExpect(jsonPath("$.content[0].total").value(0))
                .andExpect(jsonPath("$.content[0].results").doesNotExist());
    }
}
