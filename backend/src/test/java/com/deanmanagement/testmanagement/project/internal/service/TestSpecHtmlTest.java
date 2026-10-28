package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.attachment.AttachmentSummary;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.SharedStep;
import com.deanmanagement.testmanagement.project.internal.entity.StepImage;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseFolder;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestStep;
import org.junit.jupiter.api.Test;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** TM12: what the test specification says about each case, its steps and their files. */
class TestSpecHtmlTest {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);
    private static final long NO_LIMIT = Long.MAX_VALUE;

    @Test
    void textIsEscapedWithItsLineBreaksKeptAndMarkdownShownAsWritten() {
        TestCase tc = testCase("SPI-1", "Checkout");
        tc.setDescription("**Pay** <b>now</b>\nthen leave");

        String html = render(List.of(tc), NO_LIMIT);

        assertThat(html).contains("**Pay** &lt;b&gt;now&lt;/b&gt;<br/>then leave");
    }

    @Test
    void eachStepShowsItsActionTestDataAndExpectedResultInOrder() {
        TestCase tc = testCase("SPI-1", "Checkout");
        tc.getSteps().add(step(tc, 1, "Pay", "card 4242", "Paid"));
        tc.getSteps().add(step(tc, 0, "Open cart", null, null));

        String html = render(List.of(tc), NO_LIMIT);

        assertThat(html).containsSubsequence("<td>1</td><td>Open cart", "<td>2</td><td>Pay",
                "<td>card 4242</td><td>Paid</td>");
    }

    @Test
    void aSharedStepIsShownInPlaceUnderItsTitle() {
        TestCase tc = testCase("SPI-1", "Checkout");
        SharedStep login = new SharedStep();
        login.setTitle("Login");
        TestStep inner = step(null, 0, "Sign in", null, null);
        inner.setSharedStep(login);
        login.getSteps().add(inner);
        TestStep reference = step(tc, 0, "Login", null, null);
        reference.setUsesSharedStep(login);
        tc.getSteps().add(reference);

        String html = render(List.of(tc), NO_LIMIT);

        assertThat(html).contains("<span class=\"shared\">Login</span><br/>Sign in");
    }

    @Test
    void stepImagesAreEmbeddedUntilTheBudgetAndCountedAfterIt() {
        TestCase tc = testCase("SPI-1", "Checkout");
        tc.getSteps().add(withImage(step(tc, 0, "One", null, null), "one.png"));
        tc.getSteps().add(withImage(step(tc, 1, "Two", null, null), "two.png"));

        String html = render(List.of(tc), 3);

        assertThat(html.split("<img ", -1)).hasSize(2);
        assertThat(html).contains("Image: two.png", "1 more step image(s) are not embedded");
    }

    @Test
    void theFilesOfACaseAndOfItsStepsAreNamed() {
        TestCase tc = testCase("SPI-1", "Checkout");
        TestStep pay = step(tc, 0, "Pay", null, null);
        pay.setId(UUID.randomUUID());
        tc.getSteps().add(pay);

        String html = new TestSpecHtml(List.of(tc), Map.of(), Map.of(tc.getId(), List.of(file("spec.pdf"))),
                Map.of(pay.getId(), List.of(file("invoice.pdf"))), NO_LIMIT, FORMAT).build("SPI", "");

        assertThat(html).contains("Files: invoice.pdf", "Attachments: spec.pdf");
    }

    @Test
    void casesAreGroupedUnderTheirFolderPath() {
        TestCaseFolder folder = new TestCaseFolder();
        folder.setId(UUID.randomUUID());
        folder.setName("Payments");
        TestCase loose = testCase("SPI-1", "Loose");
        TestCase filed = testCase("SPI-2", "Filed");
        filed.setFolder(folder);

        String html = new TestSpecHtml(List.of(loose, filed), Map.of(folder.getId(), "Shop / Payments"), Map.of(),
                Map.of(), NO_LIMIT, FORMAT).build("SPI", "");

        assertThat(html).containsSubsequence("<h2 class=\"folder\">No folder</h2>", "SPI-1",
                "<h2 class=\"folder\">Shop / Payments</h2>", "SPI-2");
    }

    @Test
    void controlCharactersThatXmlForbidsAreDropped() {
        TestCase tc = testCase("SPI-1", "Check\u0007out");

        assertThat(render(List.of(tc), NO_LIMIT)).contains("Checkout").doesNotContain("\u0007");
    }

    // ---- helpers --------------------------------------------------------------------------------

    private static String render(List<TestCase> cases, long imageBudget) {
        return new TestSpecHtml(cases, Map.of(), Map.of(), Map.of(), imageBudget, FORMAT).build("SPI", "");
    }

    private static TestCase testCase(String key, String title) {
        TestCase tc = new TestCase();
        tc.setId(UUID.randomUUID());
        tc.setKey(key);
        tc.setTitle(title);
        tc.setStatus(TestCaseStatus.ACTIVE);
        tc.setPriority(Priority.HIGH);
        return tc;
    }

    private static TestStep step(TestCase owner, int orderIndex, String action, String testData, String expected) {
        TestStep step = new TestStep();
        step.setTestCase(owner);
        step.setOrderIndex(orderIndex);
        step.setAction(action);
        step.setTestData(testData);
        step.setExpectedResult(expected);
        return step;
    }

    private static TestStep withImage(TestStep step, String name) {
        StepImage image = new StepImage();
        image.setFileName(name);
        image.setContentType("image/png");
        image.setData(new byte[]{1, 2, 3});
        step.setImage(image);
        return step;
    }

    private static AttachmentSummary file(String name) {
        return new AttachmentSummary(UUID.randomUUID(), null, null, null, null, name, "application/pdf", 1, "x",
                null, null);
    }
}
