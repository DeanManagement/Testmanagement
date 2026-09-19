package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.StepResultResponse;
import com.deanmanagement.testmanagement.project.internal.dto.TestResultResponse;
import com.deanmanagement.testmanagement.project.internal.dto.report.RunReportOptions;
import com.deanmanagement.testmanagement.project.internal.dto.report.TestRunReportResponse;
import com.deanmanagement.testmanagement.project.internal.entity.Screenshot;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** PRD-048: what the run report PDF says about each result, its steps and screenshots. */
class RunReportHtmlTest {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);
    private static final Instant EXECUTED = Instant.parse("2026-09-19T10:15:00Z");

    @Test
    void eachResultNamesItsKeyExecutorTimeAndVersionAndTheHeaderItsPlan() {
        String html = render(RunReportOptions.RESULTS_ONLY, result(List.of(), "Tess"));

        assertThat(html).contains("Test plan: 2.1", "<td class=\"key\">SPI-7</td>", "<td>Tess</td>",
                "<td>2026-09-19 10:15</td>", "<td>v3</td>");
        assertThat(html).doesNotContain("class=\"steps\"");
    }

    @Test
    void aDeletedExecutorReadsAsUnknown() {
        String html = render(RunReportOptions.RESULTS_ONLY, result(List.of(), null));

        assertThat(html).contains("<td>Unknown user</td>");
    }

    @Test
    void withStepsEachStepHasItsStatusAndActualResult() {
        String html = render(new RunReportOptions(true, false), result(List.of(step(0, null)), "Tess"));

        assertThat(html).contains("class=\"steps\"", "<td>Pay</td>", "<td>Card declined</td>");
        assertThat(html).doesNotContain("<img");
    }

    @Test
    void withScreenshotsTheyAreEmbeddedAsImages() {
        String html = render(new RunReportOptions(false, true), result(List.of(step(0, UUID.randomUUID())), "Tess"));

        assertThat(html).contains("<img class=\"screenshot\" src=\"data:image/png;base64,");
    }

    @Test
    void screenshotsBeyondTheCapAreCountedNotEmbedded() {
        List<StepResultResponse> steps = IntStream.range(0, RunReportHtml.MAX_SCREENSHOTS + 3)
                .mapToObj(i -> step(i, UUID.randomUUID())).toList();

        String html = render(new RunReportOptions(true, true), result(steps, "Tess"));

        assertThat(html.split("<img ", -1)).hasSize(RunReportHtml.MAX_SCREENSHOTS + 1);
        assertThat(html).contains("3 more screenshots are not included");
    }

    // ---- helpers --------------------------------------------------------------------------------

    private static String render(RunReportOptions options, TestResultResponse result) {
        Screenshot png = new Screenshot();
        png.setContentType("image/png");
        png.setData(new byte[]{1, 2, 3});
        TestRunReportResponse report = new TestRunReportResponse(UUID.randomUUID(), "Nightly", null,
                TestRunStatus.COMPLETED, null, null, 1, 1, 0, 0, 0, 0, 100.0, List.of(result), Set.of(), null,
                UUID.randomUUID(), "2.1", 100.0);
        return new RunReportHtml(report, Map.of(), options, id -> Optional.of(png), FORMAT).build("SPI", "");
    }

    private static TestResultResponse result(List<StepResultResponse> steps, String executorName) {
        return new TestResultResponse(UUID.randomUUID(), UUID.randomUUID(), "SPI-7", "Checkout", null, null, null,
                TestResultStatus.FAILED, null, null, 3, null, steps, EXECUTED, UUID.randomUUID(), executorName,
                null, null, null, null, null, null);
    }

    private static StepResultResponse step(int orderIndex, UUID screenshotId) {
        return new StepResultResponse(UUID.randomUUID(), UUID.randomUUID(), "Pay", null, null, orderIndex,
                TestResultStatus.FAILED, "Card declined", screenshotId, null, null, null, null, null);
    }
}
