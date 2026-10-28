package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.dto.TestRunResponse;
import com.deanmanagement.testmanagement.project.internal.dto.UpdateTestResultRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.CreateTestRunRequest;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.project.internal.service.TestRunService;
import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.internal.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** TM21/TM22 over REST; the rules themselves are covered by McpTestRunCaseToolsApiTest. */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Transactional
class TestRunCaseApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private TestCaseRepository testCaseRepository;
    @Autowired private TestRunService testRunService;

    private String admin;
    private Project project;
    private TestCase login;
    private TestCase logout;
    private TestRunResponse run;

    @BeforeEach
    void setUp() {
        User sysAdmin = new User();
        sysAdmin.setEmail("sa-" + UUID.randomUUID() + "@test.local");
        sysAdmin.setDisplayName("sa");
        sysAdmin.setPasswordHash("x");
        sysAdmin.setSystemAdmin(true);
        admin = userRepository.save(sysAdmin).getId().toString();

        project = new Project();
        project.setName("Runs");
        project.setKey("RC" + Integer.toHexString(new java.util.Random().nextInt(0xFFFF)).toUpperCase());
        project = projectRepository.save(project);
        login = testCase("RC-1", "Login");
        logout = testCase("RC-2", "Logout");
        run = testRunService.create(project.getId(),
                new CreateTestRunRequest("Smoke", null, Set.of(login.getId(), logout.getId()), null, null), null);
    }

    private TestCase testCase(String key, String title) {
        TestCase tc = new TestCase();
        tc.setProject(project);
        tc.setKey(key);
        tc.setTitle(title);
        tc.setPriority(Priority.MEDIUM);
        tc.setStatus(TestCaseStatus.ACTIVE);
        return testCaseRepository.save(tc);
    }

    private UUID resultOf(TestCase testCase) {
        return run.results().stream().filter(r -> r.testCaseId().equals(testCase.getId())).findFirst().orElseThrow().id();
    }

    @Test
    void theOrderIsSavedAndReturned() throws Exception {
        String body = "{\"resultIds\":[\"" + resultOf(logout) + "\",\"" + resultOf(login) + "\"]}";

        mockMvc.perform(put("/api/projects/{p}/test-runs/{r}/results/order", project.getId(), run.id())
                        .contentType(MediaType.APPLICATION_JSON).content(body).with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].testCaseTitle").value("Logout"))
                .andExpect(jsonPath("$.results[0].position").value(0))
                .andExpect(jsonPath("$.results[1].testCaseTitle").value("Login"));
    }

    @Test
    void anOrderThatLeavesAResultOutIsRefused() throws Exception {
        String body = "{\"resultIds\":[\"" + resultOf(logout) + "\"]}";

        mockMvc.perform(put("/api/projects/{p}/test-runs/{r}/results/order", project.getId(), run.id())
                        .contentType(MediaType.APPLICATION_JSON).content(body).with(user(admin)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void removingARecordedResultNeedsTheCallerToSaySo() throws Exception {
        testRunService.updateResult(project.getId(), run.id(), resultOf(login),
                new UpdateTestResultRequest(TestResultStatus.PASSED, null, null, null, null), null);
        String refused = "{\"testCaseIds\":[\"" + login.getId() + "\"]}";
        String confirmed = "{\"testCaseIds\":[\"" + login.getId() + "\"],\"discardRecordedResults\":true}";

        mockMvc.perform(post("/api/projects/{p}/test-runs/{r}/test-cases/remove", project.getId(), run.id())
                        .contentType(MediaType.APPLICATION_JSON).content(refused).with(user(admin)))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/projects/{p}/test-runs/{r}/test-cases/remove", project.getId(), run.id())
                        .contentType(MediaType.APPLICATION_JSON).content(confirmed).with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[0].testCaseTitle").value("Logout"));
    }

    @Test
    void assigningToSomeoneOutsideTheProjectIsRefused() throws Exception {
        String body = "{\"resultIds\":[\"" + resultOf(login) + "\"],\"assigneeId\":\"" + UUID.randomUUID() + "\"}";

        mockMvc.perform(put("/api/projects/{p}/test-runs/{r}/results/assignee", project.getId(), run.id())
                        .contentType(MediaType.APPLICATION_JSON).content(body).with(user(admin)))
                .andExpect(status().isNotFound());
    }

    @Test
    void addingACaseAppendsAPendingResult() throws Exception {
        TestCase signup = testCase("RC-3", "Sign up");

        mockMvc.perform(post("/api/projects/{p}/test-runs/{r}/test-cases", project.getId(), run.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"testCaseIds\":[\"" + signup.getId() + "\"]}").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(3))
                .andExpect(jsonPath("$.results[2].testCaseTitle").value("Sign up"))
                .andExpect(jsonPath("$.results[2].status").value("PENDING"));
    }
}
