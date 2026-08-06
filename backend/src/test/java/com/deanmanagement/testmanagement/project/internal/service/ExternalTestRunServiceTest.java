package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.testrun.ExternalCreateTestRunRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.ExternalStepResultRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.ExternalTestResultRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.TestRunMapper;
import com.deanmanagement.testmanagement.project.internal.dto.TestRunResponse;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestRun;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestStep;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestRunRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExternalTestRunServiceTest {

    @Mock
    private TestRunRepository testRunRepository;

    @Mock
    private ExternalRefResolver refResolver;

    @Mock
    private TestCaseRepository testCaseRepository;

    @Mock
    private TestRunMapper testRunMapper;

    @Mock
    private ProjectSequenceService projectSequenceService;

    @InjectMocks
    private ExternalTestRunService externalTestRunService;

    private static final String PROJECT_KEY = "TEST";
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final String TEST_CASE_KEY = "TEST-1";
    private static final UUID STEP_ID_1 = UUID.randomUUID();
    private static final UUID STEP_ID_2 = UUID.randomUUID();
    private static final Instant NOW = Instant.now();

    private Project sampleProject() {
        Project project = new Project();
        project.setId(PROJECT_ID);
        project.setName("Test Project");
        project.setKey(PROJECT_KEY);
        return project;
    }

    private TestCase sampleTestCase() {
        TestCase tc = new TestCase();
        tc.setId(UUID.randomUUID());
        tc.setKey(TEST_CASE_KEY);
        tc.setTitle("Login Test");

        TestStep step1 = new TestStep();
        step1.setId(STEP_ID_1);
        step1.setAction("Enter username");
        step1.setExpectedResult("Username accepted");
        step1.setOrderIndex(0);
        step1.setTestCase(tc);

        TestStep step2 = new TestStep();
        step2.setId(STEP_ID_2);
        step2.setAction("Click login");
        step2.setExpectedResult("Redirected to dashboard");
        step2.setOrderIndex(1);
        step2.setTestCase(tc);

        tc.getSteps().addAll(List.of(step1, step2));
        return tc;
    }

    private TestRunResponse sampleRunResponse() {
        return new TestRunResponse(
                UUID.randomUUID(), "TEST-Run-1", "CI Run", "staging",
                TestRunStatus.COMPLETED, NOW, NOW,
                null, null, null,
                null, null, null,
                null, null,
                List.of(), NOW, NOW, null, null
        );
    }

    @Test
    void createExternalRun_withExplicitStepResults_createsRun() {
        Project project = sampleProject();
        TestCase testCase = sampleTestCase();

        var stepResults = List.of(
                new ExternalStepResultRequest(1, TestResultStatus.PASSED, "Worked"),
                new ExternalStepResultRequest(2, TestResultStatus.FAILED, "Error occurred")
        );
        var resultReq = new ExternalTestResultRequest(
                TEST_CASE_KEY, TestResultStatus.FAILED, "Login failed", "BUG-123", stepResults
        );
        var request = new ExternalCreateTestRunRequest("CI Run", "staging", List.of(resultReq));

        when(refResolver.resolveProject(PROJECT_KEY)).thenReturn(project);
        when(testCaseRepository.findByKeyAndProjectId(TEST_CASE_KEY, PROJECT_ID)).thenReturn(Optional.of(testCase));
        when(testRunRepository.save(any(TestRun.class))).thenAnswer(inv -> inv.getArgument(0));
        when(testRunMapper.toResponse(any(TestRun.class))).thenReturn(sampleRunResponse());

        externalTestRunService.createExternalRun(PROJECT_KEY, request);

        ArgumentCaptor<TestRun> captor = ArgumentCaptor.forClass(TestRun.class);
        verify(testRunRepository).save(captor.capture());
        TestRun saved = captor.getValue();

        assertThat(saved.getName()).isEqualTo("CI Run");
        assertThat(saved.getEnvironment()).isEqualTo("staging");
        assertThat(saved.getStatus()).isEqualTo(TestRunStatus.COMPLETED);
        assertThat(saved.getStartTime()).isNotNull();
        assertThat(saved.getEndTime()).isNotNull();
        assertThat(saved.getResults()).hasSize(1);

        var result = saved.getResults().getFirst();
        assertThat(result.getStatus()).isEqualTo(TestResultStatus.FAILED);
        assertThat(result.getComment()).isEqualTo("Login failed");
        assertThat(result.getDefectLink()).isEqualTo("BUG-123");
        assertThat(result.getStepResults()).hasSize(2);
        assertThat(result.getStepResults().get(0).getStatus()).isEqualTo(TestResultStatus.PASSED);
        assertThat(result.getStepResults().get(0).getActualResult()).isEqualTo("Worked");
        assertThat(result.getStepResults().get(1).getStatus()).isEqualTo(TestResultStatus.FAILED);
    }

    @Test
    void createExternalRun_withoutStepResults_autoCreatesFromTestCaseSteps() {
        Project project = sampleProject();
        TestCase testCase = sampleTestCase();

        var resultReq = new ExternalTestResultRequest(
                TEST_CASE_KEY, TestResultStatus.PASSED, null, null, null
        );
        var request = new ExternalCreateTestRunRequest("Auto Run", null, List.of(resultReq));

        when(refResolver.resolveProject(PROJECT_KEY)).thenReturn(project);
        when(testCaseRepository.findByKeyAndProjectId(TEST_CASE_KEY, PROJECT_ID)).thenReturn(Optional.of(testCase));
        when(testRunRepository.save(any(TestRun.class))).thenAnswer(inv -> inv.getArgument(0));
        when(testRunMapper.toResponse(any(TestRun.class))).thenReturn(sampleRunResponse());

        externalTestRunService.createExternalRun(PROJECT_KEY, request);

        ArgumentCaptor<TestRun> captor = ArgumentCaptor.forClass(TestRun.class);
        verify(testRunRepository).save(captor.capture());
        TestRun saved = captor.getValue();

        assertThat(saved.getResults()).hasSize(1);
        var result = saved.getResults().getFirst();
        assertThat(result.getStepResults()).hasSize(2);
        assertThat(result.getStepResults().get(0).getStatus()).isEqualTo(TestResultStatus.PASSED);
        assertThat(result.getStepResults().get(1).getStatus()).isEqualTo(TestResultStatus.PASSED);
    }

    @Test
    void createExternalRun_projectNotFound_throwsException() {
        when(refResolver.resolveProject(PROJECT_KEY))
                .thenThrow(new ResourceNotFoundException("Project", PROJECT_KEY));

        var request = new ExternalCreateTestRunRequest("Run", null, List.of(
                new ExternalTestResultRequest(TEST_CASE_KEY, TestResultStatus.PASSED, null, null, null)
        ));

        assertThatThrownBy(() -> externalTestRunService.createExternalRun(PROJECT_KEY, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Project");
    }

    @Test
    void createExternalRun_testCaseNotFound_throwsException() {
        Project project = sampleProject();
        when(refResolver.resolveProject(PROJECT_KEY)).thenReturn(project);
        when(testCaseRepository.findByKeyAndProjectId(TEST_CASE_KEY, PROJECT_ID)).thenReturn(Optional.empty());

        var request = new ExternalCreateTestRunRequest("Run", null, List.of(
                new ExternalTestResultRequest(TEST_CASE_KEY, TestResultStatus.PASSED, null, null, null)
        ));

        assertThatThrownBy(() -> externalTestRunService.createExternalRun(PROJECT_KEY, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("TestCase");
    }

    @Test
    void createExternalRun_invalidStepIndex_throwsException() {
        Project project = sampleProject();
        TestCase testCase = sampleTestCase();

        when(refResolver.resolveProject(PROJECT_KEY)).thenReturn(project);
        when(testCaseRepository.findByKeyAndProjectId(TEST_CASE_KEY, PROJECT_ID)).thenReturn(Optional.of(testCase));

        var stepResults = List.of(
                new ExternalStepResultRequest(99, TestResultStatus.PASSED, null)
        );
        var request = new ExternalCreateTestRunRequest("Run", null, List.of(
                new ExternalTestResultRequest(TEST_CASE_KEY, TestResultStatus.PASSED, null, null, stepResults)
        ));

        assertThatThrownBy(() -> externalTestRunService.createExternalRun(PROJECT_KEY, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("TestStep");
    }

    @Test
    void createExternalRun_multipleResults_createsAll() {
        Project project = sampleProject();
        TestCase testCase = sampleTestCase();

        String testCaseKey2 = "TEST-2";
        TestCase testCase2 = new TestCase();
        testCase2.setId(UUID.randomUUID());
        testCase2.setKey(testCaseKey2);
        testCase2.setTitle("Logout Test");

        var request = new ExternalCreateTestRunRequest("Multi Run", "prod", List.of(
                new ExternalTestResultRequest(TEST_CASE_KEY, TestResultStatus.PASSED, null, null, null),
                new ExternalTestResultRequest(testCaseKey2, TestResultStatus.SKIPPED, "Skipped", null, null)
        ));

        when(refResolver.resolveProject(PROJECT_KEY)).thenReturn(project);
        when(testCaseRepository.findByKeyAndProjectId(TEST_CASE_KEY, PROJECT_ID)).thenReturn(Optional.of(testCase));
        when(testCaseRepository.findByKeyAndProjectId(testCaseKey2, PROJECT_ID)).thenReturn(Optional.of(testCase2));
        when(testRunRepository.save(any(TestRun.class))).thenAnswer(inv -> inv.getArgument(0));
        when(testRunMapper.toResponse(any(TestRun.class))).thenReturn(sampleRunResponse());

        externalTestRunService.createExternalRun(PROJECT_KEY, request);

        ArgumentCaptor<TestRun> captor = ArgumentCaptor.forClass(TestRun.class);
        verify(testRunRepository).save(captor.capture());
        TestRun saved = captor.getValue();

        assertThat(saved.getResults()).hasSize(2);
        assertThat(saved.getResults().get(0).getStatus()).isEqualTo(TestResultStatus.PASSED);
        assertThat(saved.getResults().get(1).getStatus()).isEqualTo(TestResultStatus.SKIPPED);
        assertThat(saved.getResults().get(1).getComment()).isEqualTo("Skipped");
    }
}
