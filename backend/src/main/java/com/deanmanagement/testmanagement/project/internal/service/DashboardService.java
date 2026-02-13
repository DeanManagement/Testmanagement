package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.dashboard.DashboardResponse;
import com.deanmanagement.testmanagement.project.internal.dto.dashboard.DashboardResponse.DashboardTotals;
import com.deanmanagement.testmanagement.project.internal.dto.dashboard.DashboardResponse.PassRateTrendEntry;
import com.deanmanagement.testmanagement.project.internal.dto.dashboard.DashboardResponse.RecentTestRunResponse;
import com.deanmanagement.testmanagement.project.internal.entity.TestResult;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestRun;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestRunRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestSuiteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final TestCaseRepository testCaseRepository;
    private final TestRunRepository testRunRepository;
    private final TestSuiteRepository testSuiteRepository;

    public DashboardResponse getDashboard(UUID projectId) {
        long totalTestCases = testCaseRepository.countByProjectId(projectId);
        long totalTestSuites = testSuiteRepository.countByProjectId(projectId);
        long totalTestRuns = testRunRepository.countByProjectId(projectId);
        long completedTestRuns = testRunRepository.countByProjectIdAndStatus(projectId, TestRunStatus.COMPLETED);

        DashboardTotals totals = new DashboardTotals(totalTestCases, totalTestSuites, totalTestRuns, completedTestRuns);

        Map<String, Long> testCasesByStatus = new LinkedHashMap<>();
        testCaseRepository.countByProjectIdGroupByStatus(projectId)
                .forEach(row -> testCasesByStatus.put((String) row[0], (Long) row[1]));

        Map<String, Long> testCasesByPriority = new LinkedHashMap<>();
        testCaseRepository.countByProjectIdGroupByPriority(projectId)
                .forEach(row -> testCasesByPriority.put((String) row[0], (Long) row[1]));

        List<TestRun> recentRuns = testRunRepository.findTop5ByProjectIdOrderByCreatedAtDesc(projectId);
        List<RecentTestRunResponse> recentTestRuns = recentRuns.stream()
                .map(run -> {
                    List<TestResult> results = run.getResults();
                    int total = results.size();
                    int passed = (int) results.stream().filter(r -> r.getStatus() == TestResultStatus.PASSED).count();
                    int failed = (int) results.stream().filter(r -> r.getStatus() == TestResultStatus.FAILED).count();
                    return new RecentTestRunResponse(
                            run.getId(), run.getName(), run.getEnvironment(),
                            run.getStatus().name(), run.getStartTime(), run.getEndTime(),
                            total, passed, failed);
                })
                .toList();

        List<TestRun> completedRuns = testRunRepository
                .findTop10ByProjectIdAndStatusOrderByEndTimeDesc(projectId, TestRunStatus.COMPLETED);
        List<PassRateTrendEntry> passRateTrend = new ArrayList<>();
        for (TestRun run : completedRuns) {
            List<TestResult> results = run.getResults();
            int total = results.size();
            int passed = (int) results.stream().filter(r -> r.getStatus() == TestResultStatus.PASSED).count();
            double passRate = total > 0 ? Math.round(passed * 10000.0 / total) / 100.0 : 0.0;
            passRateTrend.add(new PassRateTrendEntry(run.getId(), run.getName(), run.getEndTime(), passRate));
        }
        // Reverse to show oldest first for trend chart
        passRateTrend = passRateTrend.reversed();

        return new DashboardResponse(totals, testCasesByStatus, testCasesByPriority, recentTestRuns, passRateTrend);
    }
}
