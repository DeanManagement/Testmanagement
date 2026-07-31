package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.requirement.CoverageSummaryResponse;
import com.deanmanagement.testmanagement.project.internal.dto.requirement.SaveRequirementRequest;
import com.deanmanagement.testmanagement.project.internal.dto.requirement.TraceabilityRowResponse;
import com.deanmanagement.testmanagement.project.internal.dto.requirement.TraceabilityRowResponse.CoverageStatus;
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
import com.deanmanagement.testmanagement.shared.exception.DuplicateKeyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Requirements and coverage (PRD-014). The judgement encoded here is that coverage means "a linked
 * test has passed", not "a test is linked" — so most of these tests are about the difference.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class RequirementServiceTest {

    private static final Instant BASE = Instant.parse("2026-01-01T09:00:00Z");

    @Autowired
    private RequirementService service;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private TestCaseRepository testCaseRepository;
    @Autowired
    private TestRunRepository testRunRepository;
    @Autowired
    private TestResultRepository testResultRepository;

    private final AtomicInteger sequence = new AtomicInteger();
    private Project project;

    @BeforeEach
    void setUp() {
        project = new Project();
        project.setName("Regulated");
        project.setKey("REQ" + sequence.incrementAndGet());
        project = projectRepository.save(project);
    }

    private UUID createRequirement(String externalId) {
        return service.create(project.getId(),
                new SaveRequirementRequest(externalId, "Requirement " + externalId, "why"), null).id();
    }

    private TestCase createCase(String key) {
        TestCase testCase = new TestCase();
        testCase.setProject(project);
        testCase.setKey(key);
        testCase.setTitle("Case " + key);
        testCase.setPriority(Priority.MEDIUM);
        testCase.setStatus(TestCaseStatus.ACTIVE);
        return testCaseRepository.save(testCase);
    }

    /** Records a result in a completed run, which is what "latest status" reads from. */
    private void recordResult(TestCase testCase, TestResultStatus status, int hourOffset) {
        TestRun run = new TestRun();
        run.setProject(project);
        run.setKey(testCase.getKey() + "-R" + sequence.incrementAndGet());
        run.setName("run");
        run.setStatus(TestRunStatus.COMPLETED);
        run.setEndTime(BASE.plus(hourOffset, ChronoUnit.HOURS));
        run = testRunRepository.save(run);

        TestResult result = new TestResult();
        result.setTestRun(run);
        result.setTestCase(testCase);
        result.setStatus(status);
        testResultRepository.save(result);
    }

    private TraceabilityRowResponse rowFor(UUID requirementId) {
        return service.matrix(project.getId()).stream()
                .filter(r -> r.requirementId().equals(requirementId))
                .findFirst()
                .orElseThrow();
    }

    // ---- CRUD --------------------------------------------------------------

    @Test
    void externalIdMustBeUniqueWithinAProject() {
        createRequirement("REQ-1");

        assertThatThrownBy(() -> createRequirement("REQ-1"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void theSameExternalIdIsFineInAnotherProject() {
        createRequirement("REQ-1");

        Project other = new Project();
        other.setName("Other");
        other.setKey("OTH" + sequence.incrementAndGet());
        other = projectRepository.save(other);

        // Requirement ids come from someone else's system; two projects may well both have REQ-1.
        assertThat(service.create(other.getId(),
                new SaveRequirementRequest("REQ-1", "Theirs", null), null).id()).isNotNull();
    }

    @Test
    void linkingIsIdempotent() {
        UUID requirementId = createRequirement("REQ-2");
        TestCase testCase = createCase("TC-1");

        service.linkTestCase(project.getId(), requirementId, testCase.getId(), null);
        var response = service.linkTestCase(project.getId(), requirementId, testCase.getId(), null);

        assertThat(response.testCases()).hasSize(1);
    }

    @Test
    void aTestCaseFromAnotherProjectCannotBeLinked() {
        UUID requirementId = createRequirement("REQ-3");

        Project other = new Project();
        other.setName("Other");
        other.setKey("OTH" + sequence.incrementAndGet());
        other = projectRepository.save(other);

        TestCase theirs = new TestCase();
        theirs.setProject(other);
        theirs.setKey("THEIRS-1");
        theirs.setTitle("Theirs");
        theirs.setPriority(Priority.MEDIUM);
        theirs.setStatus(TestCaseStatus.ACTIVE);
        theirs = testCaseRepository.save(theirs);

        UUID foreignCaseId = theirs.getId();
        assertThatThrownBy(() -> service.linkTestCase(project.getId(), requirementId, foreignCaseId, null))
                .hasMessageContaining("TestCase");
    }

    // ---- the coverage judgement -------------------------------------------

    @Test
    void aRequirementWithNoTestsIsUncovered() {
        UUID id = createRequirement("REQ-10");

        assertThat(rowFor(id).coverage()).isEqualTo(CoverageStatus.UNCOVERED);
    }

    @Test
    void aLinkedButNeverExecutedTestIsUntestedNotCovered() {
        UUID id = createRequirement("REQ-11");
        service.linkTestCase(project.getId(), id, createCase("TC-11").getId(), null);

        // The trap this feature exists to avoid: a requirement that looks covered on paper because
        // someone attached a test, when nothing has ever proved it.
        assertThat(rowFor(id).coverage()).isEqualTo(CoverageStatus.UNTESTED);
    }

    @Test
    void aPassingTestCoversTheRequirement() {
        UUID id = createRequirement("REQ-12");
        TestCase testCase = createCase("TC-12");
        service.linkTestCase(project.getId(), id, testCase.getId(), null);
        recordResult(testCase, TestResultStatus.PASSED, 1);

        assertThat(rowFor(id).coverage()).isEqualTo(CoverageStatus.PASSED);
    }

    @Test
    void theLatestResultWinsOverOlderOnes() {
        UUID id = createRequirement("REQ-13");
        TestCase testCase = createCase("TC-13");
        service.linkTestCase(project.getId(), id, testCase.getId(), null);
        recordResult(testCase, TestResultStatus.FAILED, 1);
        recordResult(testCase, TestResultStatus.PASSED, 5);

        assertThat(rowFor(id).coverage()).isEqualTo(CoverageStatus.PASSED);
    }

    @Test
    void oneFailingTestSinksTheWholeRequirement() {
        UUID id = createRequirement("REQ-14");
        TestCase passing = createCase("TC-14a");
        TestCase failing = createCase("TC-14b");
        service.linkTestCase(project.getId(), id, passing.getId(), null);
        service.linkTestCase(project.getId(), id, failing.getId(), null);
        recordResult(passing, TestResultStatus.PASSED, 1);
        recordResult(failing, TestResultStatus.FAILED, 1);

        // A requirement is only as proven as its weakest test.
        assertThat(rowFor(id).coverage()).isEqualTo(CoverageStatus.FAILED);
    }

    @Test
    void anUntestedCaseAmongPassingOnesStillBlocksCoverage() {
        UUID id = createRequirement("REQ-15");
        TestCase tested = createCase("TC-15a");
        TestCase untested = createCase("TC-15b");
        service.linkTestCase(project.getId(), id, tested.getId(), null);
        service.linkTestCase(project.getId(), id, untested.getId(), null);
        recordResult(tested, TestResultStatus.PASSED, 1);

        assertThat(rowFor(id).coverage()).isEqualTo(CoverageStatus.UNTESTED);
    }

    @Test
    void cellsCarryPerCaseStatusNotJustTheRollUp() {
        UUID id = createRequirement("REQ-16");
        TestCase passing = createCase("TC-16a");
        TestCase failing = createCase("TC-16b");
        service.linkTestCase(project.getId(), id, passing.getId(), null);
        service.linkTestCase(project.getId(), id, failing.getId(), null);
        recordResult(passing, TestResultStatus.PASSED, 1);
        recordResult(failing, TestResultStatus.FAILED, 1);

        List<TraceabilityRowResponse.Cell> cells = rowFor(id).cells();

        assertThat(cells).hasSize(2);
        assertThat(cells).extracting(TraceabilityRowResponse.Cell::status)
                .containsExactly(CoverageStatus.PASSED, CoverageStatus.FAILED);
    }

    // ---- coverage summary --------------------------------------------------

    @Test
    void coveragePercentCountsOnlyProvenRequirements() {
        UUID covered = createRequirement("REQ-20");
        TestCase passing = createCase("TC-20");
        service.linkTestCase(project.getId(), covered, passing.getId(), null);
        recordResult(passing, TestResultStatus.PASSED, 1);

        createRequirement("REQ-21");

        UUID linkedNotRun = createRequirement("REQ-22");
        service.linkTestCase(project.getId(), linkedNotRun, createCase("TC-22").getId(), null);

        UUID failingReq = createRequirement("REQ-23");
        TestCase failing = createCase("TC-23");
        service.linkTestCase(project.getId(), failingReq, failing.getId(), null);
        recordResult(failing, TestResultStatus.FAILED, 1);

        CoverageSummaryResponse summary = service.coverage(project.getId());

        assertThat(summary.totalRequirements()).isEqualTo(4);
        assertThat(summary.passing()).isEqualTo(1);
        assertThat(summary.uncovered()).isEqualTo(1);
        assertThat(summary.untested()).isEqualTo(1);
        assertThat(summary.failing()).isEqualTo(1);
        // One of four is actually proven — not three of four "having a test".
        assertThat(summary.coveragePercent()).isEqualTo(25.0);
    }

    @Test
    void anEmptyProjectReportsZeroRatherThanDividingByZero() {
        CoverageSummaryResponse summary = service.coverage(project.getId());

        assertThat(summary.totalRequirements()).isZero();
        assertThat(summary.coveragePercent()).isZero();
    }

    @Test
    void deletingALinkedCaseLeavesTheRequirementUncovered() {
        UUID id = createRequirement("REQ-30");
        TestCase testCase = createCase("TC-30");
        service.linkTestCase(project.getId(), id, testCase.getId(), null);

        service.unlinkTestCase(project.getId(), id, testCase.getId(), null);

        assertThat(rowFor(id).coverage()).isEqualTo(CoverageStatus.UNCOVERED);
    }
}
