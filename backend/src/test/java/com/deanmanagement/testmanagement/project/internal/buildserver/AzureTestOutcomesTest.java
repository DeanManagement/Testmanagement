package com.deanmanagement.testmanagement.project.internal.buildserver;

import com.deanmanagement.testmanagement.project.internal.ci.CiResult;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** PRD-026 §3.4: how an Azure DevOps test outcome becomes a result here. */
class AzureTestOutcomesTest {

    private final ObjectMapper json = new ObjectMapper();

    @ParameterizedTest
    @CsvSource({
            "Passed,PASSED", "Failed,FAILED", "Timeout,FAILED", "Aborted,FAILED", "Error,FAILED",
            "Blocked,BLOCKED", "NotExecuted,SKIPPED", "None,SKIPPED", "NotApplicable,SKIPPED",
            "Inconclusive,SKIPPED", "Warning,SKIPPED"
    })
    void mapsEveryOutcome(String outcome, TestResultStatus expected) {
        assertThat(AzureTestOutcomes.statusOf(outcome)).isEqualTo(expected);
    }

    @Test
    void anOutcomeFoldedIntoSkippedIsNamedInTheComment() {
        CiResult result = AzureTestOutcomes.toCiResult(json.readTree(
                "{\"outcome\":\"Inconclusive\",\"testCaseTitle\":\"Login\",\"errorMessage\":\"no verdict\"}"));

        assertThat(result.message()).isEqualTo("Azure DevOps outcome: Inconclusive\nno verdict");
    }

    @Test
    void aFailureCarriesItsMessageAndStackTrace() {
        CiResult result = AzureTestOutcomes.toCiResult(json.readTree(
                "{\"outcome\":\"Failed\",\"automatedTestName\":\"Pay.Checkout\",\"errorMessage\":\"500\",\"stackTrace\":\"at Pay\"}"));

        assertThat(result.title()).isEqualTo("Pay.Checkout");
        assertThat(result.message()).isEqualTo("500\nat Pay");
    }

    @Test
    void aResultWithoutAnyNameStillBecomesOne() {
        assertThat(AzureTestOutcomes.toCiResult(json.readTree("{\"outcome\":\"Passed\"}")).title())
                .isEqualTo("Unnamed test");
    }
}
