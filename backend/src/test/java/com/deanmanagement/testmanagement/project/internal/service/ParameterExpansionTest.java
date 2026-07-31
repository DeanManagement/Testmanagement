package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.parameter.SaveParameterSetRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.CreateTestRunRequest;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestResult;
import com.deanmanagement.testmanagement.project.internal.entity.TestStep;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Run expansion for parameterized cases (PRD-015 §3.2).
 *
 * <p>This touches the core execution flow, so the first and most important assertion is the
 * negative one: a case with no parameter sets must produce exactly what it did before this feature
 * existed.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class ParameterExpansionTest {

    @Autowired
    private TestRunService testRunService;
    @Autowired
    private ParameterSetService parameterSetService;
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
        project.setName("Parameterized");
        project.setKey("PAR" + sequence.incrementAndGet());
        project = projectRepository.save(project);
    }

    private TestCase createCase(String key, String action) {
        TestCase testCase = new TestCase();
        testCase.setProject(project);
        testCase.setKey(key);
        testCase.setTitle("Case " + key);
        testCase.setPriority(Priority.MEDIUM);
        testCase.setStatus(TestCaseStatus.ACTIVE);

        TestStep step = new TestStep();
        step.setTestCase(testCase);
        step.setAction(action);
        step.setExpectedResult("balance is {expected}");
        step.setOrderIndex(0);
        testCase.getSteps().add(step);

        return testCaseRepository.save(testCase);
    }

    private void addSet(UUID testCaseId, String name, Map<String, String> values) {
        parameterSetService.create(project.getId(), testCaseId,
                new SaveParameterSetRequest(name, values, null));
    }

    private static Map<String, String> values(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private List<TestResult> createRunAndGetResults(TestCase... cases) {
        Set<UUID> ids = new java.util.LinkedHashSet<>();
        for (TestCase tc : cases) {
            ids.add(tc.getId());
        }
        var response = testRunService.create(project.getId(), new CreateTestRunRequest(
                "Run " + sequence.incrementAndGet(), "staging", ids, null, null), null);
        return testRunRepository.findById(response.id()).orElseThrow().getResults();
    }

    // ---- the behaviour that must not change --------------------------------

    @Test
    void aCaseWithNoSetsYieldsExactlyOneResult() {
        TestCase plain = createCase("PLAIN-1", "do the thing");

        List<TestResult> results = createRunAndGetResults(plain);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getParameterSetName()).isNull();
        assertThat(results.getFirst().getParameterValuesJson()).isNull();
    }

    @Test
    void anOrdinaryCaseStillGetsItsStepResults() {
        TestCase plain = createCase("PLAIN-2", "do the thing");

        List<TestResult> results = createRunAndGetResults(plain);

        assertThat(results.getFirst().getStepResults()).hasSize(1);
    }

    // ---- expansion ---------------------------------------------------------

    @Test
    void aCaseWithThreeSetsExpandsIntoThreeResults() {
        TestCase parameterized = createCase("PARAM-1", "withdraw {amount}");
        addSet(parameterized.getId(), "minimum", values("amount", "1", "expected", "99"));
        addSet(parameterized.getId(), "typical", values("amount", "50", "expected", "50"));
        addSet(parameterized.getId(), "maximum", values("amount", "100", "expected", "0"));

        List<TestResult> results = createRunAndGetResults(parameterized);

        assertThat(results).hasSize(3);
        assertThat(results).extracting(TestResult::getParameterSetName)
                .containsExactlyInAnyOrder("minimum", "typical", "maximum");
    }

    @Test
    void eachExpandedResultCarriesItsOwnValues() {
        TestCase parameterized = createCase("PARAM-2", "withdraw {amount}");
        addSet(parameterized.getId(), "small", values("amount", "1"));
        addSet(parameterized.getId(), "large", values("amount", "999"));

        List<TestResult> results = createRunAndGetResults(parameterized);

        TestResult small = results.stream()
                .filter(r -> "small".equals(r.getParameterSetName())).findFirst().orElseThrow();
        assertThat(small.getParameterValuesJson()).contains("\"amount\"").contains("1");
    }

    @Test
    void expandedResultsEachGetTheirOwnStepResults() {
        TestCase parameterized = createCase("PARAM-3", "withdraw {amount}");
        addSet(parameterized.getId(), "a", values("amount", "1"));
        addSet(parameterized.getId(), "b", values("amount", "2"));

        List<TestResult> results = createRunAndGetResults(parameterized);

        assertThat(results).allSatisfy(r -> assertThat(r.getStepResults()).hasSize(1));
    }

    @Test
    void plainAndParameterizedCasesMixInOneRun() {
        TestCase plain = createCase("MIX-PLAIN", "do the thing");
        TestCase parameterized = createCase("MIX-PARAM", "withdraw {amount}");
        addSet(parameterized.getId(), "a", values("amount", "1"));
        addSet(parameterized.getId(), "b", values("amount", "2"));

        List<TestResult> results = createRunAndGetResults(plain, parameterized);

        assertThat(results).hasSize(3);
        assertThat(results.stream().filter(r -> r.getParameterSetName() == null)).hasSize(1);
    }

    @Test
    void removingAllSetsRevertsFutureRunsToASingleResult() {
        TestCase parameterized = createCase("REVERT-1", "withdraw {amount}");
        addSet(parameterized.getId(), "only", values("amount", "1"));
        var sets = parameterSetService.list(project.getId(), parameterized.getId());
        parameterSetService.delete(project.getId(), parameterized.getId(), sets.getFirst().id());

        assertThat(createRunAndGetResults(parameterized)).hasSize(1);
    }

    @Test
    void aRecordedResultKeepsItsValuesAfterTheSetIsDeleted() {
        TestCase parameterized = createCase("FROZEN-1", "withdraw {amount}");
        addSet(parameterized.getId(), "original", values("amount", "42"));
        List<TestResult> results = createRunAndGetResults(parameterized);
        UUID resultId = results.getFirst().getId();

        var sets = parameterSetService.list(project.getId(), parameterized.getId());
        parameterSetService.delete(project.getId(), parameterized.getId(), sets.getFirst().id());

        // History has to stay reproducible: the result recorded what it ran with.
        TestResult stored = testResultRepository.findById(resultId).orElseThrow();
        assertThat(stored.getParameterSetName()).isEqualTo("original");
        assertThat(stored.getParameterValuesJson()).contains("42");
    }

    // ---- parameter set rules ----------------------------------------------

    @Test
    void setNamesAreUniqueWithinACase() {
        TestCase parameterized = createCase("DUPE-1", "withdraw {amount}");
        addSet(parameterized.getId(), "same", values("amount", "1"));

        assertThatThrownBy(() -> addSet(parameterized.getId(), "same", values("amount", "2")))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void aKeyThatCouldNeverBeSubstitutedIsRejected() {
        TestCase parameterized = createCase("BADKEY-1", "withdraw {amount}");

        // "my amount" cannot appear in a {placeholder}, so the value would be silently unreachable.
        assertThatThrownBy(() -> addSet(parameterized.getId(), "bad", values("my amount", "1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("may contain only");
    }

    @Test
    void theNumberOfSetsPerCaseIsCapped() {
        TestCase parameterized = createCase("CAP-1", "withdraw {amount}");
        for (int i = 0; i < 50; i++) {
            addSet(parameterized.getId(), "set-" + i, values("amount", String.valueOf(i)));
        }

        // Expansion multiplies results in every run, so an accidental bulk paste must not turn one
        // run into thousands of executions.
        assertThatThrownBy(() -> addSet(parameterized.getId(), "set-50", values("amount", "50")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at most 50");
    }

    @Test
    void setsFromAnotherProjectsCaseAreNotReachable() {
        TestCase mine = createCase("SCOPE-1", "withdraw {amount}");

        Project other = new Project();
        other.setName("Other");
        other.setKey("OTH" + sequence.incrementAndGet());
        other = projectRepository.save(other);

        UUID otherProjectId = other.getId();
        UUID caseId = mine.getId();
        assertThatThrownBy(() -> parameterSetService.list(otherProjectId, caseId))
                .hasMessageContaining("TestCase");
    }
}
