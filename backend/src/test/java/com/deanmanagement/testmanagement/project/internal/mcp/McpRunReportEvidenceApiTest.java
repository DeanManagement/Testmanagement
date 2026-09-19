package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.project.internal.service.PdfReportService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The PDF is what leaves the building, so it has to say why something failed. A failure recorded
 * step by step has no result comment — its evidence is on the steps — and the report used to print
 * a dash for it.
 */
class McpRunReportEvidenceApiTest extends McpToolApiTestSupport {

    @Autowired
    private PdfReportService pdfReportService;

    @Test
    void thePdfShowsWhatWasObservedAtTheFailingStep() throws Exception {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase checkout = createCase("Checkout", "Open cart", "Submit order");
        McpDtos.CreatedTestRun run = runOf(checkout);
        stepRecordingTools.recordStepResult(run.key(), 1, TestResultStatus.PASSED, checkout.id(),
                null, "Cart opened");
        stepRecordingTools.recordStepResult(run.key(), 2, TestResultStatus.FAILED, checkout.id(),
                null, "Order button returns HTTP 500");
        testRunWriteTools.completeTestRun(run.key(), null, null);

        byte[] pdf = pdfReportService.generateTestRunReport(project.getId(), run.id());

        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
            assertThat(text).contains("Step 2: Order button returns HTTP 500");
            assertThat(text).doesNotContain("Cart opened");
        }
    }
}
