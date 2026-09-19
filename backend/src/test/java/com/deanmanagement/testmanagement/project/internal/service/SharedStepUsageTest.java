package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.ci.CiResult;
import com.deanmanagement.testmanagement.project.internal.dto.StepResultResponse;
import com.deanmanagement.testmanagement.project.internal.dto.TestRunResponse;
import com.deanmanagement.testmanagement.project.internal.dto.TestStepRequest;
import com.deanmanagement.testmanagement.project.internal.dto.TestStepResponse;
import com.deanmanagement.testmanagement.project.internal.dto.UpdateTestCaseRequest;
import com.deanmanagement.testmanagement.project.internal.dto.parameter.SaveParameterSetRequest;
import com.deanmanagement.testmanagement.project.internal.dto.sharedStep.SaveSharedStepRequest;
import com.deanmanagement.testmanagement.project.internal.dto.sharedStep.SharedStepResponse;
import com.deanmanagement.testmanagement.project.internal.dto.sharedStep.SharedStepStepRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.CreateTestCaseRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.TestCaseResponse;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.CreateTestRunRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.ExternalCreateTestRunRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.ExternalStepResultRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.ExternalTestResultRequest;
import com.deanmanagement.testmanagement.project.internal.dto.version.TestCaseVersionResponse.StepSnapshot;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.StepImage;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestStep;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestStepRepository;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/** Test cases that reference shared blocks, and everything that runs them (PRD-030 §3.2). */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class SharedStepUsageTest {

    @Autowired private SharedStepService sharedStepService;
    @Autowired private TestCaseService testCaseService;
    @Autowired private TestRunService testRunService;
    @Autowired private ParameterSetService parameterSetService;
    @Autowired private TestCaseVersionService versionService;
    @Autowired private CiIngestionService ciIngestionService;
    @Autowired private ExternalTestRunService externalTestRunService;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private TestCaseRepository testCaseRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TestStepRepository testStepRepository;
    @Autowired private EntityManager entityManager;

    private Project project;
    private SharedStepResponse login;

    @BeforeEach
    void setUp() {
        project = new Project();
        project.setName("Shared usage");
        project.setKey("SU" + Integer.toHexString(new java.util.Random().nextInt(0xFFFF)).toUpperCase());
        project = projectRepository.save(project);
        login = sharedStepService.create(project.getId(), new SaveSharedStepRequest("Log in", null, List.of(
                new SharedStepStepRequest(null, "Open the login page", "Form shown", null),
                new SharedStepStepRequest(null, "Sign in as {user}", "Dashboard", null))), null);
    }

    private static TestStepRequest local(String action) {
        return new TestStepRequest(action, "", null);
    }

    private static TestStepRequest reference(UUID blockId) {
        return new TestStepRequest(null, null, null, blockId);
    }

    /** Local, then the login block, then local: four steps as executed. */
    private TestCaseResponse checkoutCase() {
        return testCaseService.create(project.getId(), new CreateTestCaseRequest("Checkout", null, null,
                Priority.MEDIUM, TestCaseStatus.ACTIVE, Set.of(),
                List.of(local("Reset the basket"), reference(login.id()), local("Pay")), null), null);
    }

    /** Runs and ingestion read fresh from the database, as in production, not the cached entities. */
    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private static List<String> actionsInOrder(TestRunResponse run) {
        return run.results().getFirst().stepResults().stream()
                .sorted(Comparator.comparingInt(StepResultResponse::orderIndex))
                .map(StepResultResponse::action)
                .toList();
    }

    private void editLoginSteps(List<SharedStepStepRequest> steps) {
        sharedStepService.update(project.getId(), login.id(), new SaveSharedStepRequest("Log in", null, steps), null);
    }

    @Nested
    class Saving {

        @Test
        void aCaseMixesLocalStepsAndAReferenceThatExpandsInTheResponse() {
            TestStepResponse ref = checkoutCase().steps().get(1);

            assertThat(ref.sharedStepId()).isEqualTo(login.id());
            assertThat(ref.sharedStepTitle()).isEqualTo("Log in");
            assertThat(ref.action()).isEqualTo("Log in");
            assertThat(ref.expandedSteps()).extracting(TestStepResponse::action)
                    .containsExactly("Open the login page", "Sign in as {user}");
        }

        @Test
        void aBlockOfAnotherProjectIsNotFound() {
            Project other = new Project();
            other.setName("Other");
            other.setKey("OT" + Integer.toHexString(new java.util.Random().nextInt(0xFFFF)).toUpperCase());
            other = projectRepository.save(other);
            UUID otherProject = other.getId();

            assertThatThrownBy(() -> testCaseService.create(otherProject, new CreateTestCaseRequest("X", null, null,
                    Priority.MEDIUM, TestCaseStatus.DRAFT, Set.of(), List.of(reference(login.id())), null), null))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void aStepWithNeitherTextNorABlockIsRefused() {
            assertThatThrownBy(() -> testCaseService.create(project.getId(), new CreateTestCaseRequest("X", null, null,
                    Priority.MEDIUM, TestCaseStatus.DRAFT, Set.of(), List.of(new TestStepRequest(" ", null, null)), null), null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Step 1");
        }
    }

    @Nested
    class Running {

        @Test
        void aNewRunExpandsTheBlockInPlaceWithItsOwnOrder() {
            UUID caseId = checkoutCase().id();
            flushAndClear();

            TestRunResponse run = testRunService.create(project.getId(), new CreateTestRunRequest("Run", null,
                    Set.of(caseId), null, null, null, null, null), null);

            List<StepResultResponse> steps = run.results().getFirst().stepResults().stream()
                    .sorted(Comparator.comparingInt(StepResultResponse::orderIndex)).toList();
            assertThat(steps).extracting(StepResultResponse::orderIndex, StepResultResponse::action,
                    StepResultResponse::sharedStepTitle).containsExactly(
                    tuple(0, "Reset the basket", null),
                    tuple(1, "Open the login page", "Log in"),
                    tuple(2, "Sign in as {user}", "Log in"),
                    tuple(3, "Pay", null));
        }

        @Test
        void eachParameterSetGetsTheExpandedSteps() {
            UUID caseId = checkoutCase().id();
            parameterSetService.create(project.getId(), caseId, new SaveParameterSetRequest("admin", Map.of("user", "admin"), null));
            parameterSetService.create(project.getId(), caseId, new SaveParameterSetRequest("guest", Map.of("user", "guest"), null));
            flushAndClear();

            TestRunResponse run = testRunService.create(project.getId(), new CreateTestRunRequest("Run", null,
                    Set.of(caseId), null, null, null, null, null), null);

            assertThat(run.results()).hasSize(2).allSatisfy(r -> assertThat(r.stepResults()).hasSize(4));
        }

        @Test
        void aResultWrittenBeforePositionsIsOrderedByItsStep() {
            TestCaseResponse tc = testCaseService.create(project.getId(), new CreateTestCaseRequest("Old", null, null,
                    Priority.MEDIUM, TestCaseStatus.ACTIVE, Set.of(), List.of(local("First"), local("Second")), null), null);
            flushAndClear();
            TestRunResponse run = testRunService.create(project.getId(), new CreateTestRunRequest("Run", null,
                    Set.of(tc.id()), null, null, null, null, null), null);
            flushAndClear();
            jdbcTemplate.update("UPDATE step_results SET position = NULL");

            TestRunResponse reread = testRunService.findById(project.getId(), run.id());

            assertThat(actionsInOrder(reread)).containsExactly("First", "Second");
        }

        @Test
        void ciStepResultsAreMatchedAgainstTheExpandedSteps() {
            TestCaseResponse tc = checkoutCase();
            flushAndClear();
            List<CiResult.CiStep> ciSteps = List.of(
                    new CiResult.CiStep("reset", TestResultStatus.PASSED),
                    new CiResult.CiStep("open", TestResultStatus.PASSED),
                    new CiResult.CiStep("sign in", TestResultStatus.FAILED),
                    new CiResult.CiStep("pay", TestResultStatus.SKIPPED));

            TestRunResponse run = ciIngestionService.ingest(project.getKey(), "CI", null, null, List.of(new CiResult(
                    "s", "t", TestResultStatus.FAILED, null, ciSteps, null, tc.key())), null, null);

            assertThat(run.results().getFirst().stepResults().stream()
                    .sorted(Comparator.comparingInt(StepResultResponse::orderIndex)))
                    .extracting(StepResultResponse::action, StepResultResponse::status).containsExactly(
                            tuple("Reset the basket", TestResultStatus.PASSED),
                            tuple("Open the login page", TestResultStatus.PASSED),
                            tuple("Sign in as {user}", TestResultStatus.FAILED),
                            tuple("Pay", TestResultStatus.SKIPPED));
        }

        @Test
        void anExternalStepIndexCountsTheExpandedSteps() {
            TestCaseResponse tc = checkoutCase();
            flushAndClear();

            TestRunResponse run = externalTestRunService.createExternalRun(project.getKey(),
                    new ExternalCreateTestRunRequest("Ext", null, List.of(new ExternalTestResultRequest(tc.key(),
                            TestResultStatus.FAILED, null, null,
                            List.of(new ExternalStepResultRequest(3, TestResultStatus.FAILED, "wrong password")), null))),
                    null, null);

            StepResultResponse step = run.results().getFirst().stepResults().getFirst();
            assertThat(step.action()).isEqualTo("Sign in as {user}");
            assertThat(step.orderIndex()).isEqualTo(2);
        }
    }

    @Nested
    class Versions {

        @Test
        void aSnapshotHoldsTheExpandedStepsWithTheirBlock() {
            TestCaseResponse tc = checkoutCase();
            testCaseService.update(project.getId(), tc.id(), new UpdateTestCaseRequest("Checkout v2", null, null,
                    null, null, null, null), null);

            List<StepSnapshot> steps = versionService.get(project.getId(), tc.id(), 1).steps();

            assertThat(steps).extracting(StepSnapshot::orderIndex, StepSnapshot::action, StepSnapshot::sharedStepTitle)
                    .containsExactly(tuple(0, "Reset the basket", null), tuple(1, "Open the login page", "Log in"),
                            tuple(2, "Sign in as {user}", "Log in"), tuple(3, "Pay", null));
        }

        @Test
        void editingABlockWritesAPreEditVersionOnEveryCaseUsingIt() {
            TestCaseResponse tc = checkoutCase();
            List<UUID> stepIds = login.steps().stream().map(TestStepResponse::id).toList();

            editLoginSteps(List.of(new SharedStepStepRequest(stepIds.get(0), "Open the NEW login page", "Form shown", null),
                    new SharedStepStepRequest(stepIds.get(1), "Sign in as {user}", "Dashboard", null)));

            assertThat(versionService.list(project.getId(), tc.id())).hasSize(2);
            assertThat(versionService.get(project.getId(), tc.id(), 1).steps().get(1).action())
                    .isEqualTo("Open the login page");
            assertThat(versionService.get(project.getId(), tc.id(), 2).steps().get(1).action())
                    .isEqualTo("Open the NEW login page");
        }

        @Test
        void renamingABlockWithoutTouchingItsStepsWritesNoVersion() {
            TestCaseResponse tc = checkoutCase();
            List<SharedStepStepRequest> same = login.steps().stream()
                    .map(s -> new SharedStepStepRequest(s.id(), s.action(), s.expectedResult(), s.testData())).toList();

            sharedStepService.update(project.getId(), login.id(), new SaveSharedStepRequest("Log in as admin", null, same), null);

            assertThat(versionService.list(project.getId(), tc.id())).hasSize(1);
            assertThat(testCaseService.findById(project.getId(), tc.id()).steps().get(1).action()).isEqualTo("Log in as admin");
        }

        @Test
        void anApprovedCaseGoesBackToReviewWhenItsBlockChanges() {
            UUID caseId = checkoutCase().id();
            project.setReviewRequired(true);
            projectRepository.save(project);
            TestCase tc = testCaseRepository.findById(caseId).orElseThrow();
            tc.setApprovedVersion(tc.getCurrentVersion()); // approved, as review would leave it
            UUID first = login.steps().getFirst().id();

            editLoginSteps(List.of(new SharedStepStepRequest(first, "Open the login page", "Form shown", null)));

            assertThat(testCaseRepository.findById(caseId).orElseThrow().getStatus()).isEqualTo(TestCaseStatus.IN_REVIEW);
        }
    }

    @Nested
    class Inlining {

        @Test
        void aReferenceBecomesCopiesOfTheBlockStepsInItsPlace() {
            TestCaseResponse tc = checkoutCase();
            // As in production, where the case was saved by an earlier request: Hibernate only
            // orphan-deletes collection elements it has flushed before.
            flushAndClear();

            TestCaseResponse inlined = testCaseService.inlineSharedStep(project.getId(), tc.id(),
                    tc.steps().get(1).id(), null);

            assertThat(inlined.steps()).extracting(TestStepResponse::action, TestStepResponse::sharedStepId,
                    TestStepResponse::orderIndex).containsExactly(
                    tuple("Reset the basket", null, 0), tuple("Open the login page", null, 1),
                    tuple("Sign in as {user}", null, 2), tuple("Pay", null, 3));
            assertThat(inlined.steps().get(1).expectedResult()).isEqualTo("Form shown");
            assertThat(versionService.list(project.getId(), tc.id())).hasSize(2);
            flushAndClear();
            assertThat(sharedStepService.usages(project.getId(), login.id())).isEmpty();
        }

        @Test
        void aBlockStepImageIsCopiedNotShared() {
            TestStep blockStep = testStepRepository.findById(login.steps().getFirst().id()).orElseThrow();
            StepImage image = new StepImage();
            image.setFileName("login.png");
            image.setContentType("image/png");
            image.setData(new byte[]{1, 2, 3});
            image.setTestStep(blockStep);
            blockStep.setImage(image);
            TestCaseResponse tc = checkoutCase();
            flushAndClear();

            TestStepResponse copy = testCaseService.inlineSharedStep(project.getId(), tc.id(), tc.steps().get(1).id(), null)
                    .steps().get(1);
            flushAndClear();

            assertThat(copy.imageId()).isNotNull();
            assertThat(testStepRepository.findById(blockStep.getId()).orElseThrow().getImage().getId())
                    .isNotEqualTo(copy.imageId());
        }

        @Test
        void aLocalStepCannotBeInlined() {
            TestCaseResponse tc = checkoutCase();

            assertThatThrownBy(() -> testCaseService.inlineSharedStep(project.getId(), tc.id(), tc.steps().get(0).id(), null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
