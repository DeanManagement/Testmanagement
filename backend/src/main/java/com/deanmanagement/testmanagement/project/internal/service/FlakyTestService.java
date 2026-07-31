package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.analytics.FlakyResultRow;
import com.deanmanagement.testmanagement.project.internal.dto.analytics.FlakyTestResponse;
import com.deanmanagement.testmanagement.project.internal.entity.AuditAction;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Flakiness analytics over existing result history (PRD-016).
 *
 * <p>A test is flaky when its outcome keeps changing without anyone changing the test. So the score
 * counts <em>transitions</em>, not failures: a test that fails every time is broken, not flaky, and
 * scores zero. Alternating pass/fail scores one.
 *
 * <p>Computed on demand rather than cached. At this tool's scale the query is a few thousand narrow
 * rows and the dashboard asks for it once per visit; a cached column and a nightly job would be two
 * more things to keep correct for no measurable gain. Revisit if the query shows up in profiling.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FlakyTestService {

    private final TestResultRepository testResultRepository;
    private final TestCaseRepository testCaseRepository;
    private final FlakyProperties properties;
    private final AuditService auditService;

    /** Every case with enough history, most flaky first. */
    public List<FlakyTestResponse> analyse(UUID projectId) {
        List<FlakyResultRow> rows = testResultRepository.findTerminalResultsForFlakiness(projectId);

        // The query orders by (case, time desc), so grouping preserves newest-first per case.
        Map<UUID, List<FlakyResultRow>> byCase = new LinkedHashMap<>();
        for (FlakyResultRow row : rows) {
            byCase.computeIfAbsent(row.testCaseId(), key -> new ArrayList<>()).add(row);
        }

        List<FlakyTestResponse> scored = new ArrayList<>();
        for (List<FlakyResultRow> caseRows : byCase.values()) {
            scored.add(score(caseRows));
        }
        scored.sort(Comparator
                .comparingDouble(FlakyTestResponse::flakyScore).reversed()
                .thenComparing(Comparator.comparingInt(FlakyTestResponse::runsConsidered).reversed())
                .thenComparing(FlakyTestResponse::testCaseKey));
        return scored;
    }

    /** The dashboard widget's view: only cases that actually qualify as flaky, capped. */
    public List<FlakyTestResponse> findFlaky(UUID projectId, int limit) {
        return analyse(projectId).stream()
                .filter(FlakyTestResponse::flaky)
                .limit(Math.max(1, limit))
                .toList();
    }

    /**
     * Scores one case from its results, newest first.
     *
     * <p>Only the most recent {@code window} results count, so a test fixed six months ago stops
     * being reported as flaky once enough clean runs accumulate.
     */
    private FlakyTestResponse score(List<FlakyResultRow> newestFirst) {
        FlakyResultRow head = newestFirst.getFirst();
        List<FlakyResultRow> window = newestFirst.size() > properties.window()
                ? newestFirst.subList(0, properties.window())
                : newestFirst;

        int considered = window.size();
        long failures = window.stream().filter(r -> r.status() == TestResultStatus.FAILED).count();

        // Pairs, not results: n results give n-1 chances to change outcome.
        int comparablePairs = Math.max(0, considered - 1);
        int transitions = 0;
        for (int i = 0; i < considered - 1; i++) {
            if (window.get(i).status() != window.get(i + 1).status()) {
                transitions++;
            }
        }

        double flakyScore = comparablePairs == 0 ? 0.0 : (double) transitions / comparablePairs;
        double failRate = considered == 0 ? 0.0 : (double) failures / considered;
        // Below min-runs the score is real but not trustworthy: one flip out of two results reads
        // as 1.0, which would put a barely-executed case at the top of the list.
        boolean flaky = considered >= properties.minRuns() && flakyScore >= properties.threshold();

        return new FlakyTestResponse(
                head.testCaseId(),
                head.testCaseKey(),
                head.testCaseTitle(),
                round(flakyScore),
                round(failRate),
                considered,
                flaky);
    }

    /**
     * Syncs the {@code flaky} label to match current scoring, when enabled.
     *
     * <p>Off by default: labels are user-owned and every change lands in the audit log, so a tool
     * that silently retags test cases on a timer is noisy and surprising.
     */
    @Transactional
    public int syncLabels(UUID projectId, UUID actorId) {
        if (!properties.autoLabel()) {
            return 0;
        }
        List<FlakyTestResponse> scored = analyse(projectId);
        int changed = 0;

        for (FlakyTestResponse result : scored) {
            TestCase testCase = testCaseRepository.findById(result.testCaseId()).orElse(null);
            if (testCase == null) {
                continue;
            }
            boolean hasLabel = testCase.getLabels().contains(properties.label());
            if (result.flaky() == hasLabel) {
                continue;
            }

            if (result.flaky()) {
                testCase.getLabels().add(properties.label());
            } else {
                testCase.getLabels().remove(properties.label());
            }
            testCaseRepository.save(testCase);
            auditService.log(projectId, actorId, AuditAction.UPDATED,
                    AuditEntityType.TEST_CASE, testCase.getId(), testCase.getKey(),
                    (result.flaky() ? "Added" : "Removed") + " the '" + properties.label()
                            + "' label (score " + result.flakyScore() + ")");
            changed++;
        }
        return changed;
    }

    /** Two decimals is all the precision the score carries or the UI shows. */
    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
