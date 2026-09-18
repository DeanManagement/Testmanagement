package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.TestRunResponse;
import com.deanmanagement.testmanagement.project.internal.dto.environment.EnvironmentResultResponse;
import com.deanmanagement.testmanagement.project.internal.dto.parameter.SaveParameterSetRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.CreateTestRunRequest;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestPlan;
import com.deanmanagement.testmanagement.project.internal.entity.TestPlanStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestRun;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestPlanRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestRunRepository;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Creating runs across environments, and a case's latest result per environment (PRD-032). */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class EnvironmentRunsTest {

    @Autowired
    private TestRunService testRunService;
    @Autowired
    private ProjectEnvironmentService environmentService;
    @Autowired
    private ParameterSetService parameterSetService;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private TestCaseRepository testCaseRepository;
    @Autowired
    private TestPlanRepository testPlanRepository;
    @Autowired
    private TestRunRepository testRunRepository;

    private Project project;
    private TestCase testCase;
    private UUID staging;
    private UUID production;

    @BeforeEach
    void setUp() {
        project = new Project();
        project.setName("Environments");
        project.setKey("MENV");
        project = projectRepository.save(project);

        testCase = new TestCase();
        testCase.setProject(project);
        testCase.setKey("MENV-1");
        testCase.setTitle("Checkout");
        testCase.setPriority(Priority.MEDIUM);
        testCase.setStatus(TestCaseStatus.ACTIVE);
        testCase = testCaseRepository.save(testCase);

        staging = environmentService.resolve(project.getId(), null, "Staging").getId();
        production = environmentService.resolve(project.getId(), null, "Production").getId();
    }

    private CreateTestRunRequest acrossRequest(List<UUID> environmentIds, UUID planId) {
        return new CreateTestRunRequest("Release", null, Set.of(testCase.getId()), planId, null, null, environmentIds);
    }

    @Nested
    class AcrossEnvironments {

        @Test
        void createsOneRunPerEnvironmentWithTheSameCasesAndPlan() {
            parameterSetService.create(project.getId(), testCase.getId(),
                    new SaveParameterSetRequest("visa", Map.of("card", "visa"), null));
            parameterSetService.create(project.getId(), testCase.getId(),
                    new SaveParameterSetRequest("amex", Map.of("card", "amex"), null));
            TestPlan plan = new TestPlan();
            plan.setProject(project);
            plan.setName("2.4");
            plan.setStatus(TestPlanStatus.OPEN);
            UUID planId = testPlanRepository.save(plan).getId();

            List<TestRunResponse> runs = testRunService.createAcrossEnvironments(project.getId(),
                    acrossRequest(List.of(staging, production), planId), null);

            assertThat(runs).extracting(TestRunResponse::name)
                    .containsExactly("Release · Staging", "Release · Production");
            assertThat(runs).extracting(TestRunResponse::environment).containsExactly("Staging", "Production");
            assertThat(runs).extracting(TestRunResponse::key).doesNotHaveDuplicates();
            assertThat(runs).allSatisfy(run -> {
                assertThat(run.testPlanId()).isEqualTo(planId);
                assertThat(run.results()).as("parameter sets expand in every run").hasSize(2);
            });
        }

        @Test
        void ignoresDuplicateIds() {
            List<TestRunResponse> runs = testRunService.createAcrossEnvironments(project.getId(),
                    acrossRequest(List.of(staging, staging), null), null);

            assertThat(runs).hasSize(1);
        }

        @Test
        void refusesAForeignEnvironmentBeforeCreatingAnything() {
            Project other = new Project();
            other.setName("Other");
            other.setKey("OTHR");
            UUID foreign = environmentService.resolve(projectRepository.save(other).getId(), null, "qa").getId();

            assertThatThrownBy(() -> testRunService.createAcrossEnvironments(project.getId(),
                    acrossRequest(List.of(staging, foreign), null), null))
                    .isInstanceOf(ResourceNotFoundException.class);
            assertThat(testRunRepository.countByProjectId(project.getId())).isZero();
        }

        @Test
        void refusesMoreThanTheLimit() {
            List<UUID> tooMany = new ArrayList<>();
            for (int i = 0; i <= CreateTestRunRequest.MAX_ENVIRONMENTS; i++) {
                tooMany.add(environmentService.resolve(project.getId(), null, "env-" + i).getId());
            }

            assertThatThrownBy(() -> testRunService.createAcrossEnvironments(project.getId(),
                    acrossRequest(tooMany, null), null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void refusesEnvironmentIdsOnThePlainCreate() {
            assertThatThrownBy(() -> testRunService.create(project.getId(),
                    acrossRequest(List.of(staging), null), null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("across-environments");
        }

        @Test
        void refusesNoEnvironments() {
            assertThatThrownBy(() -> testRunService.createAcrossEnvironments(project.getId(),
                    acrossRequest(Collections.emptyList(), null), null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class LatestResultByEnvironment {

        /** Creates a run with one executed result, ending at {@code endTime}. */
        private void executed(String environment, TestResultStatus status, Instant endTime) {
            TestRunResponse created = testRunService.create(project.getId(), new CreateTestRunRequest(
                    "Run", environment, Set.of(testCase.getId()), null, null), null);
            TestRun run = testRunRepository.findById(created.id()).orElseThrow();
            run.getResults().forEach(result -> result.setStatus(status));
            run.setEndTime(endTime);
            testRunRepository.saveAndFlush(run);
        }

        @Test
        void ordersByRunTimeNotInsertTime() {
            Instant now = Instant.now();
            executed("Staging", TestResultStatus.FAILED, now);
            // Inserted later, but CI backfilled an older run.
            executed("Staging", TestResultStatus.PASSED, now.minus(Duration.ofDays(1)));

            List<EnvironmentResultResponse> rows = environmentService.latestResultsByEnvironment(project.getId(),
                    testCase.getId());

            assertThat(rows).singleElement().satisfies(row -> {
                assertThat(row.environmentName()).isEqualTo("Staging");
                assertThat(row.status()).isEqualTo(TestResultStatus.FAILED);
            });
        }

        @Test
        void givesOneRowPerEnvironmentWithUnspecifiedLast() {
            Instant now = Instant.now();
            executed("Staging", TestResultStatus.PASSED, now.minusSeconds(60));
            executed("Production", TestResultStatus.FAILED, now);
            executed(null, TestResultStatus.BLOCKED, now.plusSeconds(60));

            List<EnvironmentResultResponse> rows = environmentService.latestResultsByEnvironment(project.getId(),
                    testCase.getId());

            assertThat(rows).extracting(EnvironmentResultResponse::environmentName)
                    .containsExactly("Production", "Staging", null);
        }

        @Test
        void ignoresPendingResults() {
            testRunService.create(project.getId(), new CreateTestRunRequest(
                    "Planned", "Staging", Set.of(testCase.getId()), null, null), null);

            assertThat(environmentService.latestResultsByEnvironment(project.getId(), testCase.getId())).isEmpty();
        }

        @Test
        void refusesACaseFromAnotherProject() {
            assertThatThrownBy(() -> environmentService.latestResultsByEnvironment(UUID.randomUUID(), testCase.getId()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
