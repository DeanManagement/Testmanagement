package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestResult;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestRun;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import com.deanmanagement.testmanagement.project.internal.entity.WebhookEventType;
import com.deanmanagement.testmanagement.project.internal.webhook.WebhookEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RunEventPublisherTest {

    private final List<Object> published = new ArrayList<>();
    private final RunEventPublisher publisher = new RunEventPublisher(published::add);

    private static TestRun runWith(TestResultStatus... statuses) {
        Project project = new Project();
        project.setId(UUID.randomUUID());
        TestRun run = new TestRun();
        run.setId(UUID.randomUUID());
        run.setProject(project);
        run.setKey("P-Run-1");
        run.setName("Nightly");
        run.setEnvironment("staging");
        run.setStatus(TestRunStatus.COMPLETED);
        for (int i = 0; i < statuses.length; i++) {
            TestCase testCase = new TestCase();
            testCase.setKey("P-" + i);
            testCase.setTitle("Case " + i);
            TestResult result = new TestResult();
            result.setTestCase(testCase);
            result.setStatus(statuses[i], null);
            run.getResults().add(result);
        }
        return run;
    }

    private List<WebhookEventType> publishedTypes() {
        return published.stream().map(e -> ((WebhookEvent) e).type()).toList();
    }

    @Test
    void finishedRunWithoutFailuresPublishesCompletedOnly() {
        publisher.publishFinished(runWith(TestResultStatus.PASSED));

        assertThat(publishedTypes()).containsExactly(WebhookEventType.RUN_COMPLETED);
    }

    @Test
    void finishedRunWithFailuresAlsoPublishesFailed() {
        publisher.publishFinished(runWith(TestResultStatus.PASSED, TestResultStatus.FAILED));

        assertThat(publishedTypes()).containsExactly(WebhookEventType.RUN_COMPLETED, WebhookEventType.RUN_FAILED);
    }

    @Test
    void dataCarriesEnvironmentAndCappedFailedTests() {
        TestResultStatus[] statuses = new TestResultStatus[RunEventPublisher.MAX_FAILED_TESTS + 2];
        Arrays.fill(statuses, TestResultStatus.FAILED);

        publisher.publishFinished(runWith(statuses));

        Map<String, Object> data = ((WebhookEvent) published.get(0)).data();
        assertThat(data.get("environment")).isEqualTo("staging");
        assertThat(data.get("failed")).isEqualTo(RunEventPublisher.MAX_FAILED_TESTS + 2);
        assertThat((List<?>) data.get("failedTests")).hasSize(RunEventPublisher.MAX_FAILED_TESTS)
                .first().isEqualTo(Map.of("key", "P-0", "title", "Case 0"));
    }

    /** PRD-049: pending results are not failures; a run that executed nothing has no pass rate. */
    @Test
    void passRateCountsOnlyExecutedResults() {
        publisher.publishFinished(runWith(TestResultStatus.PASSED, TestResultStatus.PENDING, TestResultStatus.PENDING));

        Map<String, Object> data = ((WebhookEvent) published.get(0)).data();
        assertThat(data.get("passRate")).isEqualTo(100.0);
        assertThat(data.get("executed")).isEqualTo(1);
    }

    @Test
    void aRunThatExecutedNothingHasNoPassRate() {
        publisher.publishFinished(runWith(TestResultStatus.PENDING));

        assertThat(((WebhookEvent) published.get(0)).data().get("passRate")).isNull();
    }
}
