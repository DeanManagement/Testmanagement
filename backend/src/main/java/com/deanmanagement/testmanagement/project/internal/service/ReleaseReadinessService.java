package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.analytics.FlakyTestResponse;
import com.deanmanagement.testmanagement.project.internal.dto.readiness.ReadinessResponse;
import com.deanmanagement.testmanagement.project.internal.dto.readiness.ReadinessResponse.Counts;
import com.deanmanagement.testmanagement.project.internal.dto.readiness.ReadinessResponse.Criterion;
import com.deanmanagement.testmanagement.project.internal.dto.readiness.ReadinessResponse.CriterionName;
import com.deanmanagement.testmanagement.project.internal.dto.readiness.ReadinessResponse.Outcome;
import com.deanmanagement.testmanagement.project.internal.dto.readiness.ReadinessResponse.Verdict;
import com.deanmanagement.testmanagement.project.internal.dto.readiness.ReadinessResultRow;
import com.deanmanagement.testmanagement.project.internal.dto.requirement.CoverageSummaryResponse;
import com.deanmanagement.testmanagement.project.internal.dto.testplan.ReleaseGate;
import com.deanmanagement.testmanagement.project.internal.dto.testplan.TestPlanMapper;
import com.deanmanagement.testmanagement.project.internal.entity.BugReportStatus;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.TestPlan;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.project.internal.repository.BugReportRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestPlanRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestResultRepository;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Release readiness of a test plan (PRD-037): aggregation over data the tool already has, judged
 * against the plan's optional gate. Loading is the only I/O; {@link #evaluate} is pure.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReleaseReadinessService {

    /** Bugs at this priority block a release. One line to widen if HIGH should block too. */
    static final Priority BLOCKER_PRIORITY = Priority.CRITICAL;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int PERCENT_DECIMALS = 2;

    private final TestPlanRepository testPlanRepository;
    private final TestResultRepository testResultRepository;
    private final BugReportRepository bugReportRepository;
    private final RequirementService requirementService;
    private final FlakyTestService flakyTestService;
    private final TestPlanMapper testPlanMapper;
    private final Clock clock;

    /** What the gate is judged on, already reduced to numbers. */
    record Inputs(Counts counts, long blockerBugs, BigDecimal coveragePercent, long flakyCases) {
    }

    public ReadinessResponse readiness(UUID projectId, UUID planId) {
        TestPlan plan = testPlanRepository.findByIdAndProjectId(planId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("TestPlan", planId));
        ReleaseGate gate = testPlanMapper.gateOf(plan);
        Inputs inputs = load(projectId, planId, gate);
        List<Criterion> criteria = evaluate(gate, inputs);
        return new ReadinessResponse(plan.getId(), plan.getName(), verdictOf(criteria), clock.instant(), criteria,
                inputs.counts());
    }

    /** Reads only what the configured criteria need: the flaky scan is project-wide and not free. */
    private Inputs load(UUID projectId, UUID planId, ReleaseGate gate) {
        List<ReadinessResultRow> rows = testResultRepository.findReadinessRows(planId);
        long blockerBugs = gate.maxBlockerBugs() == null ? 0
                : bugReportRepository.countByProjectIdAndPriorityAndStatusIn(projectId, BLOCKER_PRIORITY, BugReportStatus.OPEN_STATUSES);
        BigDecimal coverage = gate.minCoverage() == null ? null : coverageOf(requirementService.coverage(projectId));
        long flaky = gate.maxFlaky() == null ? 0 : flakyCasesIn(rows, flakyTestService.analyse(projectId));
        return new Inputs(countLatest(rows), blockerBugs, coverage, flaky);
    }

    /** Null when the project has no requirements: the criterion then does not apply. */
    private static BigDecimal coverageOf(CoverageSummaryResponse coverage) {
        return coverage.totalRequirements() == 0 ? null : BigDecimal.valueOf(coverage.coveragePercent());
    }

    /** A flaky case this plan never touches is not this release's problem. */
    static long flakyCasesIn(List<ReadinessResultRow> rows, List<FlakyTestResponse> analysis) {
        Set<UUID> executedInPlan = rows.stream().map(ReadinessResultRow::testCaseId).collect(Collectors.toSet());
        return analysis.stream()
                .filter(FlakyTestResponse::flaky)
                .map(FlakyTestResponse::testCaseId)
                .filter(executedInPlan::contains)
                .distinct()
                .count();
    }

    /**
     * Counts the latest result per case and parameter set: a case that failed on Monday and passed
     * on Wednesday's retest is one pass. PENDING stays pending, which the pass rate treats as not
     * passed: an unexecuted test is not evidence.
     */
    static Counts countLatest(Collection<ReadinessResultRow> rows) {
        Map<ResultKey, ReadinessResultRow> latest = new LinkedHashMap<>();
        Comparator<ReadinessResultRow> newer = Comparator.comparing(ReadinessResultRow::runAt)
                .thenComparing(ReadinessResultRow::resultUpdatedAt);
        for (ReadinessResultRow row : rows) {
            latest.merge(new ResultKey(row.testCaseId(), row.parameterSetName()), row,
                    (a, b) -> newer.compare(a, b) >= 0 ? a : b);
        }
        Map<TestResultStatus, Long> byStatus = latest.values().stream()
                .collect(Collectors.groupingBy(ReadinessResultRow::status, Collectors.counting()));
        return new Counts(latest.size(), count(byStatus, TestResultStatus.PASSED), count(byStatus, TestResultStatus.FAILED),
                count(byStatus, TestResultStatus.BLOCKED), count(byStatus, TestResultStatus.SKIPPED),
                count(byStatus, TestResultStatus.PENDING));
    }

    /** A parameterized case is one result per set (PRD-015); an ordinary case has a null set. */
    private record ResultKey(UUID testCaseId, String parameterSetName) {
    }

    private static int count(Map<TestResultStatus, Long> byStatus, TestResultStatus status) {
        return byStatus.getOrDefault(status, 0L).intValue();
    }

    /** One entry per configured criterion. A value equal to its threshold passes. */
    static List<Criterion> evaluate(ReleaseGate gate, Inputs inputs) {
        List<Criterion> criteria = new ArrayList<>();
        if (gate.minPassRate() != null) {
            criteria.add(passRate(gate.minPassRate(), inputs.counts()));
        }
        if (gate.maxBlockerBugs() != null) {
            criteria.add(atMost(CriterionName.BLOCKER_BUGS, inputs.blockerBugs(), gate.maxBlockerBugs()));
        }
        if (gate.minCoverage() != null) {
            criteria.add(coverage(gate.minCoverage(), inputs.coveragePercent()));
        }
        if (gate.maxFlaky() != null) {
            criteria.add(atMost(CriterionName.FLAKY_TESTS, inputs.flakyCases(), gate.maxFlaky()));
        }
        return criteria;
    }

    static Verdict verdictOf(List<Criterion> criteria) {
        if (criteria.isEmpty()) {
            return Verdict.NO_CRITERIA;
        }
        return criteria.stream().anyMatch(c -> c.outcome() == Outcome.FAIL) ? Verdict.NO_GO : Verdict.GO;
    }

    /** Compared exactly (passed × 100 ≥ threshold × considered), rounded only for display. */
    private static Criterion passRate(BigDecimal threshold, Counts counts) {
        if (counts.considered() == 0) {
            // Nothing executed proves nothing: a plan without results is not ready.
            return new Criterion(CriterionName.PASS_RATE, BigDecimal.ZERO, threshold, Outcome.FAIL);
        }
        BigDecimal passed = BigDecimal.valueOf(counts.passed());
        BigDecimal considered = BigDecimal.valueOf(counts.considered());
        boolean meets = passed.multiply(HUNDRED).compareTo(threshold.multiply(considered)) >= 0;
        BigDecimal actual = passed.multiply(HUNDRED).divide(considered, PERCENT_DECIMALS, RoundingMode.HALF_UP);
        return new Criterion(CriterionName.PASS_RATE, actual, threshold, meets ? Outcome.PASS : Outcome.FAIL);
    }

    private static Criterion coverage(BigDecimal threshold, BigDecimal actual) {
        if (actual == null) {
            return new Criterion(CriterionName.COVERAGE, null, threshold, Outcome.NOT_APPLICABLE);
        }
        Outcome outcome = actual.compareTo(threshold) >= 0 ? Outcome.PASS : Outcome.FAIL;
        return new Criterion(CriterionName.COVERAGE, actual.setScale(PERCENT_DECIMALS, RoundingMode.HALF_UP), threshold,
                outcome);
    }

    private static Criterion atMost(CriterionName name, long actual, int threshold) {
        return new Criterion(name, BigDecimal.valueOf(actual), BigDecimal.valueOf(threshold),
                actual <= threshold ? Outcome.PASS : Outcome.FAIL);
    }
}
