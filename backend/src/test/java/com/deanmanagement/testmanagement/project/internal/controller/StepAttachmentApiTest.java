package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.dto.TestRunResponse;
import com.deanmanagement.testmanagement.project.internal.dto.TestStepRequest;
import com.deanmanagement.testmanagement.project.internal.dto.TestStepResponse;
import com.deanmanagement.testmanagement.project.internal.dto.UpdateTestCaseRequest;
import com.deanmanagement.testmanagement.project.internal.dto.sharedStep.SaveSharedStepRequest;
import com.deanmanagement.testmanagement.project.internal.dto.sharedStep.SharedStepResponse;
import com.deanmanagement.testmanagement.project.internal.dto.sharedStep.SharedStepStepRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.CreateTestCaseRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.TestCaseResponse;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.CreateTestRunRequest;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.service.SharedStepService;
import com.deanmanagement.testmanagement.project.internal.service.TestCaseService;
import com.deanmanagement.testmanagement.project.internal.service.TestCaseVersionService;
import com.deanmanagement.testmanagement.project.internal.service.TestRunService;
import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.internal.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TM05 (PRD-053 §1): documents on a test step and several evidence files on a step result. The
 * commit path (files surviving a step replacement) is proven by McpTestCaseAttachmentToolsApiTest,
 * which is not transactional.
 */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Transactional
class StepAttachmentApiTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
    private static final byte[] PDF = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private TestCaseService testCaseService;
    @Autowired private TestCaseVersionService versionService;
    @Autowired private SharedStepService sharedStepService;
    @Autowired private TestRunService testRunService;
    @Autowired private EntityManager entityManager;

    private String admin;
    private UUID projectId;
    private TestCaseResponse testCase;

    @BeforeEach
    void setUp() {
        User u = new User();
        u.setEmail("sa-" + UUID.randomUUID() + "@test.local");
        u.setDisplayName("sa");
        u.setPasswordHash("x");
        u.setSystemAdmin(true);
        admin = userRepository.save(u).getId().toString();

        Project project = new Project();
        project.setName("Step files");
        project.setKey("STF");
        projectId = projectRepository.save(project).getId();
        testCase = testCaseService.create(projectId, new CreateTestCaseRequest("Case", null, null, Priority.MEDIUM,
                TestCaseStatus.ACTIVE, Set.of(), List.of(new TestStepRequest("Open", null, null),
                new TestStepRequest("Pay", null, null)), null), null);
    }

    @Test
    void aStepFileIsListedForTheCaseStepsButNotAmongTheCasesOwnFiles() throws Exception {
        UUID payStep = stepId(1);
        uploadToStep(payStep, "invoice.pdf", "application/pdf", PDF).andExpect(status().isCreated());

        mockMvc.perform(get("/api/projects/{p}/test-cases/{c}/step-attachments", projectId, testCase.id())
                        .with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].testStepId").value(payStep.toString()))
                .andExpect(jsonPath("$[0].fileName").value("invoice.pdf"));
        mockMvc.perform(get("/api/projects/{p}/test-cases/{c}/attachments", projectId, testCase.id())
                        .with(user(admin)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void aFileFollowsItsStepByIdWhenTheStepsAreReordered() throws Exception {
        uploadToStep(stepId(1), "invoice.pdf", "application/pdf", PDF).andExpect(status().isCreated());
        List<TestStepResponse> steps = testCase.steps();

        TestCaseResponse updated = testCaseService.update(projectId, testCase.id(), new UpdateTestCaseRequest(
                null, null, null, null, null, null, List.of(
                        new TestStepRequest("Pay", null, null, null, steps.get(1).id()),
                        new TestStepRequest("Open", null, null, null, steps.get(0).id()))), null);

        mockMvc.perform(get("/api/projects/{p}/test-cases/{c}/step-attachments", projectId, testCase.id())
                        .with(user(admin)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].testStepId").value(updated.steps().getFirst().id().toString()));
    }

    @Test
    void aStepsImageAndFilesMoveTogetherAndARemovedStepsFilesGo() throws Exception {
        List<TestStepResponse> steps = testCase.steps();
        mockMvc.perform(multipart("/api/step-images").file(new MockMultipartFile("file", "ref.png", "image/png", PNG))
                .param("testStepId", steps.get(1).id().toString()).with(user(admin))).andExpect(status().isCreated());
        uploadToStep(steps.get(1).id(), "invoice.pdf", "application/pdf", PDF).andExpect(status().isCreated());
        uploadToStep(steps.get(0).id(), "gone.pdf", "application/pdf", PDF).andExpect(status().isCreated());

        TestCaseResponse updated = testCaseService.update(projectId, testCase.id(), new UpdateTestCaseRequest(
                null, null, null, null, null, null, List.of(
                        new TestStepRequest("Pay", null, null, null, steps.get(1).id()))), null);

        TestStepResponse pay = updated.steps().getFirst();
        assertThat(pay.imageId()).isNotNull();
        mockMvc.perform(get("/api/projects/{p}/test-cases/{c}/step-attachments", projectId, testCase.id())
                        .with(user(admin)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fileName").value("invoice.pdf"))
                .andExpect(jsonPath("$[0].testStepId").value(pay.id().toString()));
        mockMvc.perform(get("/api/projects/{p}/test-cases/{c}/attachments", projectId, testCase.id())
                        .with(user(admin)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void theVersionBeforeAnEditRecordsTheStepsFileNames() throws Exception {
        uploadToStep(stepId(1), "invoice.pdf", "application/pdf", PDF).andExpect(status().isCreated());

        testCaseService.update(projectId, testCase.id(), new UpdateTestCaseRequest(null, null, null, null, null,
                null, List.of(new TestStepRequest("Open", null, null))), null);

        assertThat(versionService.get(projectId, testCase.id(), 1).steps().get(1).attachments())
                .containsExactly("invoice.pdf");
    }

    @Test
    void aSharedStepsFileShowsInEveryCaseUsingIt() throws Exception {
        SharedStepResponse block = sharedStepService.create(projectId, new SaveSharedStepRequest("Login", null,
                List.of(new SharedStepStepRequest(null, "Sign in", null, null))), null);
        uploadToStep(block.steps().getFirst().id(), "users.csv", "text/csv", "a,b\n".getBytes())
                .andExpect(status().isCreated());
        testCaseService.update(projectId, testCase.id(), new UpdateTestCaseRequest(null, null, null, null, null,
                null, List.of(new TestStepRequest(null, null, null, block.id()))), null);

        mockMvc.perform(get("/api/projects/{p}/test-cases/{c}/step-attachments", projectId, testCase.id())
                        .with(user(admin)))
                .andExpect(jsonPath("$[0].fileName").value("users.csv"));
    }

    @Test
    void convertingASharedStepToLocalStepsCopiesItsFiles() throws Exception {
        SharedStepResponse block = sharedStepService.create(projectId, new SaveSharedStepRequest("Login", null,
                List.of(new SharedStepStepRequest(null, "Sign in", null, null))), null);
        UUID blockStep = block.steps().getFirst().id();
        uploadToStep(blockStep, "users.csv", "text/csv", "a,b\n".getBytes()).andExpect(status().isCreated());
        TestCaseResponse withReference = testCaseService.update(projectId, testCase.id(), new UpdateTestCaseRequest(
                null, null, null, null, null, null, List.of(new TestStepRequest(null, null, null, block.id()))), null);

        TestCaseResponse converted = testCaseService.inlineSharedStep(projectId, testCase.id(),
                withReference.steps().getFirst().id(), null);

        UUID localStep = converted.steps().getFirst().id();
        mockMvc.perform(get("/api/projects/{p}/test-cases/{c}/step-attachments", projectId, testCase.id())
                        .with(user(admin)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].testStepId").value(localStep.toString()));
        mockMvc.perform(get("/api/projects/{p}/test-steps/{s}/attachments", projectId, blockStep).with(user(admin)))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void aStepResultHoldsSeveralEvidenceFilesListedWithTheRun() throws Exception {
        uploadToStep(stepId(0), "spec.pdf", "application/pdf", PDF).andExpect(status().isCreated());
        TestRunResponse run = testRunService.create(projectId, new CreateTestRunRequest("Run", null,
                Set.of(testCase.id()), null, null, null, null, null), null);
        UUID stepResultId = run.results().getFirst().stepResults().getFirst().id();
        String evidence = "/api/projects/" + projectId + "/step-results/" + stepResultId + "/attachments";

        mockMvc.perform(multipart(evidence).file(new MockMultipartFile("file", "shot.png", "image/png", PNG))
                .with(user(admin))).andExpect(status().isCreated());
        mockMvc.perform(multipart(evidence).file(new MockMultipartFile("file", "log.pdf", "application/pdf", PDF))
                .with(user(admin))).andExpect(status().isCreated());

        mockMvc.perform(get(evidence).with(user(admin)))
                .andExpect(jsonPath("$[*].fileName").value(org.hamcrest.Matchers.contains("shot.png", "log.pdf")));
        String forRun = mockMvc.perform(get("/api/projects/{p}/test-runs/{r}/step-attachments", projectId, run.id())
                        .with(user(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(forRun, "$[*].fileName"))
                .containsExactlyInAnyOrder("spec.pdf", "shot.png", "log.pdf");
    }

    @Test
    void aStepOfAnotherProjectIsNotFound() throws Exception {
        Project other = new Project();
        other.setName("Other");
        other.setKey("OTF");
        UUID otherProject = projectRepository.save(other).getId();

        mockMvc.perform(multipart("/api/projects/" + otherProject + "/test-steps/" + stepId(0) + "/attachments")
                        .file(new MockMultipartFile("file", "spec.pdf", "application/pdf", PDF)).with(user(admin)))
                .andExpect(status().isNotFound());
    }

    private UUID stepId(int index) {
        return testCase.steps().get(index).id();
    }

    /**
     * Uploads, then flushes and clears as the end of the request would: a later edit in this same
     * test transaction must not find the upload's entity still managed, which no real request does.
     */
    private ResultActions uploadToStep(UUID stepId, String name, String type, byte[] data) throws Exception {
        ResultActions result = mockMvc.perform(multipart("/api/projects/" + projectId + "/test-steps/" + stepId
                + "/attachments").file(new MockMultipartFile("file", name, type, data)).with(user(admin)));
        entityManager.flush();
        entityManager.clear();
        return result;
    }
}
