package com.deanmanagement.testmanagement.project.internal.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** A default Allure 3 report reads localStorage before anything else and died in the sandbox (TM39). */
class AllureStorageShimTest {

    private static String shimmed(String html) {
        return new String(AllureReportService.withStorageShim(html.getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8);
    }

    @Test
    void theStandInRunsBeforeTheReportsOwnScripts() {
        String page = shimmed("<html><head lang=\"en\"><script src=\"app.js\"></script></head></html>");

        assertThat(page.indexOf("localStorage")).isPositive().isLessThan(page.indexOf("app.js"));
        assertThat(page).startsWith("<html><head lang=\"en\"><script>");
    }

    @Test
    void aPageWithoutAHeadStillGetsTheStandInFirst() {
        assertThat(shimmed("<script src=\"app.js\"></script>")).startsWith("<script>(function");
    }
}
