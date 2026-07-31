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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Transactional
class BulkResultStatusApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private TestCaseRepository testCaseRepository;
    @Autowired private TestRunRepository testRunRepository;
    @Autowired private TestResultRepository testResultRepository;

    private UUID projectId;
    private UUID runId;
    private String admin;
    private UUID resultA;
    private UUID resultB;

    @BeforeEach
    void setUp() {
        User sysAdmin = new User();
        sysAdmin.setEmail("sa-" + UUID.randomUUID() + "@test.local");
        sysAdmin.setDisplayName("sa");
        sysAdmin.setPasswordHash("x");
        sysAdmin.setSystemAdmin(true);
        admin = userRepository.save(sysAdmin).getId().toString();

        Project project = new Project();
        project.setName("Bulk Project");
        project.setKey("BULK");
        projectId = projectRepository.save(project).getId();

        TestRun run = new TestRun();
        run.setProject(project);
        run.setName("Run");
        run.setKey("BULK-Run-1");
        run.setStatus(TestRunStatus.IN_PROGRESS);
        runId = testRunRepository.save(run).getId();

        resultA = newResult(project, run, "TC A", "BULK-1");
        resultB = newResult(project, run, "TC B", "BULK-2");
    }

    private UUID newResult(Project project, TestRun run, String title, String key) {
        TestCase tc = new TestCase();
        tc.setProject(project);
        tc.setTitle(title);
        tc.setKey(key);
        tc.setStatus(TestCaseStatus.ACTIVE);
        tc.setPriority(Priority.MEDIUM);
        tc = testCaseRepository.save(tc);

        TestResult result = new TestResult();
        result.setTestRun(run);
        result.setTestCase(tc);
        result.setStatus(TestResultStatus.PENDING);
        return testResultRepository.save(result).getId();
    }

    @Test
    void bulkSetStatus_updatesAll() throws Exception {
        String body = "{\"resultIds\":[\"" + resultA + "\",\"" + resultB + "\"],\"status\":\"SKIPPED\",\"cascadeSteps\":false}";
        mockMvc.perform(post("/api/projects/{p}/test-runs/{r}/results/bulk-status", projectId, runId)
                        .with(user(admin)).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affected").value(2));

        assertThat(testResultRepository.findById(resultA).orElseThrow().getStatus())
                .isEqualTo(TestResultStatus.SKIPPED);
        assertThat(testResultRepository.findById(resultB).orElseThrow().getStatus())
                .isEqualTo(TestResultStatus.SKIPPED);
    }

    @Test
    void overLimit_returns400() throws Exception {
        String ids = Stream.generate(() -> "\"" + UUID.randomUUID() + "\"").limit(101).collect(Collectors.joining(","));
        String body = "{\"resultIds\":[" + ids + "],\"status\":\"BLOCKED\",\"cascadeSteps\":false}";
        mockMvc.perform(post("/api/projects/{p}/test-runs/{r}/results/bulk-status", projectId, runId)
                        .with(user(admin)).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resultsNotInRun_returns400() throws Exception {
        String body = "{\"resultIds\":[\"" + UUID.randomUUID() + "\"],\"status\":\"BLOCKED\",\"cascadeSteps\":false}";
        mockMvc.perform(post("/api/projects/{p}/test-runs/{r}/results/bulk-status", projectId, runId)
                        .with(user(admin)).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
    }
}
