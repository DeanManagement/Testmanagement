package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.entity.TestResult;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestRun;
import com.deanmanagement.testmanagement.project.internal.entity.WebhookEventType;
import com.deanmanagement.testmanagement.project.internal.webhook.WebhookEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Publishes run-level webhook events. Shared by manual status transitions ({@link TestRunService})
 * and CI ingestion (PRD-031 §3.2), so every way a run finishes notifies the same way.
 */
@Component
@RequiredArgsConstructor
public class RunEventPublisher {

    static final int MAX_FAILED_TESTS = 10;

    private final ApplicationEventPublisher eventPublisher;

    public void publishStarted(TestRun run) {
        publish(WebhookEventType.RUN_STARTED, run);
    }

    /** {@code RUN_COMPLETED}, followed by {@code RUN_FAILED} when any result failed. */
    public void publishFinished(TestRun run) {
        publish(WebhookEventType.RUN_COMPLETED, run);
        if (count(run.getResults(), TestResultStatus.FAILED) > 0) {
            publish(WebhookEventType.RUN_FAILED, run);
        }
    }

    private void publish(WebhookEventType type, TestRun run) {
        List<TestResult> results = run.getResults();
        int total = results.size();
        int passed = count(results, TestResultStatus.PASSED);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("runId", run.getId().toString());
        data.put("runKey", run.getKey());
        data.put("name", run.getName());
        data.put("status", run.getStatus().name());
        data.put("environment", run.getEnvironment());
        data.put("total", total);
        data.put("passed", passed);
        data.put("failed", count(results, TestResultStatus.FAILED));
        data.put("blocked", count(results, TestResultStatus.BLOCKED));
        data.put("skipped", count(results, TestResultStatus.SKIPPED));
        PassRate rate = PassRate.of(results.stream().map(TestResult::getStatus).toList());
        // PRD-049: passed of executed; null when nothing ran, rather than a 0 % that reads as failure.
        data.put("passRate", rate.percent());
        data.put("executed", rate.executed());
        data.put("failedTests", failedTests(results));
        eventPublisher.publishEvent(new WebhookEvent(type, run.getProject().getId(), data));
    }

    private static List<Map<String, Object>> failedTests(List<TestResult> results) {
        return results.stream()
                .filter(r -> r.getStatus() == TestResultStatus.FAILED && r.getTestCase() != null)
                .limit(MAX_FAILED_TESTS)
                .map(r -> {
                    Map<String, Object> test = new LinkedHashMap<>();
                    test.put("key", r.getTestCase().getKey());
                    test.put("title", r.getTestCase().getTitle());
                    return test;
                })
                .toList();
    }

    private static int count(List<TestResult> results, TestResultStatus status) {
        return (int) results.stream().filter(r -> r.getStatus() == status).count();
    }
}
