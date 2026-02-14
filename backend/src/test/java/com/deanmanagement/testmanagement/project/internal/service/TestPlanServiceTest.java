package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.testplan.CreateTestPlanRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testplan.TestPlanMapper;
import com.deanmanagement.testmanagement.project.internal.dto.testplan.TestPlanResponse;
import com.deanmanagement.testmanagement.project.internal.dto.testplan.TestPlanSummaryResponse;
import com.deanmanagement.testmanagement.project.internal.dto.testplan.UpdateTestPlanRequest;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.TestPlan;
import com.deanmanagement.testmanagement.project.internal.entity.TestPlanStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestResult;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestRun;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestPlanRepository;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestPlanServiceTest {

    @Mock
    private TestPlanRepository testPlanRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private TestPlanMapper testPlanMapper;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private TestPlanService testPlanService;

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID PLAN_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.now();

    private Project sampleProject() {
        Project project = new Project();
        project.setId(PROJECT_ID);
        project.setName("Test Project");
        project.setKey("TP");
        return project;
    }

    private TestPlan samplePlan() {
        TestPlan plan = new TestPlan();
        plan.setId(PLAN_ID);
        plan.setName("Release 2.3");
        plan.setDescription("All tests for v2.3");
        plan.setStatus(TestPlanStatus.OPEN);
        plan.setTargetDate(LocalDate.of(2026, 3, 15));
        plan.setProject(sampleProject());
        return plan;
    }

    private TestPlanResponse sampleResponse() {
        return new TestPlanResponse(
                PLAN_ID, "Release 2.3", "All tests for v2.3",
                TestPlanStatus.OPEN, LocalDate.of(2026, 3, 15),
                0, NOW, NOW
        );
    }

    @Test
    void findByProject_returnsPlans() {
        TestPlan plan = samplePlan();
        when(testPlanRepository.findByProjectIdOrderByCreatedAtDesc(PROJECT_ID)).thenReturn(List.of(plan));
        when(testPlanMapper.toResponse(plan)).thenReturn(sampleResponse());

        List<TestPlanResponse> result = testPlanService.findByProject(PROJECT_ID);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("Release 2.3");
    }

    @Test
    void findById_returnsPlan() {
        TestPlan plan = samplePlan();
        when(testPlanRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));
        when(testPlanMapper.toResponse(plan)).thenReturn(sampleResponse());

        TestPlanResponse result = testPlanService.findById(PROJECT_ID, PLAN_ID);

        assertThat(result.name()).isEqualTo("Release 2.3");
    }

    @Test
    void findById_notFound_throwsException() {
        when(testPlanRepository.findById(PLAN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> testPlanService.findById(PROJECT_ID, PLAN_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_savesAndReturnsPlan() {
        var request = new CreateTestPlanRequest("Release 2.3", "Description", LocalDate.of(2026, 3, 15));
        Project project = sampleProject();
        TestPlan plan = samplePlan();

        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(testPlanMapper.toEntity(request)).thenReturn(plan);
        when(testPlanRepository.save(plan)).thenReturn(plan);
        when(testPlanMapper.toResponse(plan)).thenReturn(sampleResponse());

        TestPlanResponse result = testPlanService.create(PROJECT_ID, request, null);

        assertThat(result).isNotNull();
        assertThat(plan.getStatus()).isEqualTo(TestPlanStatus.OPEN);
        verify(testPlanRepository).save(any(TestPlan.class));
    }

    @Test
    void create_projectNotFound_throwsException() {
        var request = new CreateTestPlanRequest("Release 2.3", null, null);
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> testPlanService.create(PROJECT_ID, request, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_updatesAndReturnsPlan() {
        var request = new UpdateTestPlanRequest("Updated Name", "Updated desc", TestPlanStatus.IN_PROGRESS, null);
        TestPlan plan = samplePlan();

        when(testPlanRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));
        when(testPlanRepository.save(plan)).thenReturn(plan);
        when(testPlanMapper.toResponse(plan)).thenReturn(sampleResponse());

        TestPlanResponse result = testPlanService.update(PROJECT_ID, PLAN_ID, request, null);

        assertThat(result).isNotNull();
        assertThat(plan.getName()).isEqualTo("Updated Name");
        assertThat(plan.getStatus()).isEqualTo(TestPlanStatus.IN_PROGRESS);
    }

    @Test
    void delete_removesPlan() {
        TestPlan plan = samplePlan();
        when(testPlanRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));

        testPlanService.delete(PROJECT_ID, PLAN_ID, null);

        verify(testPlanRepository).delete(plan);
    }

    @Test
    void delete_notFound_throwsException() {
        when(testPlanRepository.findById(PLAN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> testPlanService.delete(PROJECT_ID, PLAN_ID, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getSummary_aggregatesStatsAcrossRuns() {
        TestPlan plan = samplePlan();

        TestRun run1 = new TestRun();
        run1.setId(UUID.randomUUID());
        run1.setName("Run 1");
        run1.setStatus(TestRunStatus.COMPLETED);

        TestResult r1 = new TestResult();
        r1.setStatus(TestResultStatus.PASSED);
        TestResult r2 = new TestResult();
        r2.setStatus(TestResultStatus.FAILED);
        run1.getResults().addAll(List.of(r1, r2));

        TestRun run2 = new TestRun();
        run2.setId(UUID.randomUUID());
        run2.setName("Run 2");
        run2.setStatus(TestRunStatus.IN_PROGRESS);

        TestResult r3 = new TestResult();
        r3.setStatus(TestResultStatus.PASSED);
        run2.getResults().add(r3);

        plan.getTestRuns().addAll(List.of(run1, run2));

        when(testPlanRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));

        TestPlanSummaryResponse summary = testPlanService.getSummary(PROJECT_ID, PLAN_ID);

        assertThat(summary.totalRuns()).isEqualTo(2);
        assertThat(summary.completedRuns()).isEqualTo(1);
        assertThat(summary.totalResults()).isEqualTo(3);
        assertThat(summary.passed()).isEqualTo(2);
        assertThat(summary.failed()).isEqualTo(1);
        assertThat(summary.passRate()).isGreaterThan(0);
        assertThat(summary.runs()).hasSize(2);
    }
}
