package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.StepResultResponse;
import com.deanmanagement.testmanagement.project.internal.dto.TestResultResponse;
import com.deanmanagement.testmanagement.project.internal.dto.report.RunReportOptions;
import com.deanmanagement.testmanagement.project.internal.dto.report.TestRunReportResponse;
import com.deanmanagement.testmanagement.project.internal.dto.attachment.AttachmentSummary;
import com.deanmanagement.testmanagement.project.internal.entity.Attachment;
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
import java.util.stream.Collectors;
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
        String html = render(new RunReportOptions(true, false), result(List.of(step(0)), "Tess"));

        assertThat(html).contains("class=\"steps\"", "<td>Pay</td>", "<td>Card declined</td>");
        assertThat(html).doesNotContain("<img");
    }

    @Test
    void aMultiLineStepActionKeepsItsLineBreaksAndStaysEscapedText() {
        StepResultResponse step = new StepResultResponse(UUID.randomUUID(), UUID.randomUUID(),
                "**Open** the cart\n<b>Pay</b>", null, null, 0, TestResultStatus.PASSED, null, null, null, null, null, null);

        String html = render(new RunReportOptions(true, false), result(List.of(step), "Tess"));

        assertThat(html).contains("<td>**Open** the cart<br/>&lt;b&gt;Pay&lt;/b&gt;</td>");
    }

    @Test
    void withScreenshotsTheyAreEmbeddedAsImages() {
        String html = render(new RunReportOptions(false, true), result(List.of(step(0)), "Tess"));

        assertThat(html).contains("<img class=\"screenshot\" src=\"data:image/png;base64,");
    }

    @Test
    void evidenceThatIsNotAnImageIsListedByName() {
        String html = render(new RunReportOptions(true, true), result(List.of(step(0)), "Tess"));

        assertThat(html).contains("<th>Evidence</th>", "log &lt;1&gt;.pdf<br/>");
    }

    @Test
    void screenshotsBeyondTheCapAreCountedNotEmbedded() {
        List<StepResultResponse> steps = IntStream.range(0, RunReportHtml.MAX_SCREENSHOTS + 3)
                .mapToObj(i -> step(i)).toList();

        String html = render(new RunReportOptions(true, true), result(steps, "Tess"));

        assertThat(html.split("<img ", -1)).hasSize(RunReportHtml.MAX_SCREENSHOTS + 1);
        assertThat(html).contains("3 more screenshots are not included");
    }

    // ---- helpers --------------------------------------------------------------------------------

    private static String render(RunReportOptions options, TestResultResponse result) {
        Attachment png = new Attachment();
        png.setContentType("image/png");
        png.setData(new byte[]{1, 2, 3});
        TestRunReportResponse report = new TestRunReportResponse(UUID.randomUUID(), "Nightly", null,
                TestRunStatus.COMPLETED, null, null, 1, 1, 0, 0, 0, 0, 100.0, List.of(result), Set.of(), null,
                UUID.randomUUID(), "2.1", 100.0);
        Map<UUID, List<AttachmentSummary>> evidence = result.stepResults().stream()
                .filter(step -> step.actualResult() != null)
                .collect(Collectors.toMap(StepResultResponse::id, step -> List.of(shot(step.id()), document(step.id()))));
        return new RunReportHtml(report, Map.of(), options, evidence, id -> Optional.of(png), FORMAT).build("SPI", "");
    }

    private static TestResultResponse result(List<StepResultResponse> steps, String executorName) {
        return new TestResultResponse(UUID.randomUUID(), UUID.randomUUID(), "SPI-7", "Checkout", null, null, null,
                TestResultStatus.FAILED, null, null, 3, null, steps, EXECUTED, UUID.randomUUID(), executorName,
                null, null, null, null, null, null, 0, null, null, null, null);
    }

    /** A step with evidence: a screenshot and a document (TM05). */
    private static StepResultResponse step(int orderIndex) {
        return new StepResultResponse(UUID.randomUUID(), UUID.randomUUID(), "Pay", null, null, orderIndex,
                TestResultStatus.FAILED, "Card declined", null, null, null, null, null);
    }

    private static AttachmentSummary shot(UUID stepResultId) {
        return new AttachmentSummary(UUID.randomUUID(), null, null, null, stepResultId, "shot.png", "image/png", 3,
                "abc", null, null);
    }

    private static AttachmentSummary document(UUID stepResultId) {
        return new AttachmentSummary(UUID.randomUUID(), null, null, null, stepResultId, "log <1>.pdf",
                "application/pdf", 3, "def", null, null);
    }
}
