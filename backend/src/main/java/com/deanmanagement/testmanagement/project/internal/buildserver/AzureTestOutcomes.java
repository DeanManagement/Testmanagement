package com.deanmanagement.testmanagement.project.internal.buildserver;

import com.deanmanagement.testmanagement.project.internal.ci.CiResult;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Locale;

/**
 * Turns one Azure DevOps test result into a {@link CiResult} (PRD-026 §3.4). Pure, so the outcome
 * table is tested without a server.
 */
final class AzureTestOutcomes {

    private AzureTestOutcomes() {
    }

    static CiResult toCiResult(JsonNode result) {
        String outcome = text(result, "outcome");
        TestResultStatus status = statusOf(outcome);
        String title = firstNonBlank(text(result, "testCaseTitle"), text(result, "automatedTestName"));
        return new CiResult(
                text(result, "automatedTestStorage"),
                title == null ? "Unnamed test" : title,
                status,
                message(result, outcome, status),
                List.of(),
                durationMs(result));
    }

    static TestResultStatus statusOf(String outcome) {
        if (outcome == null) {
            return TestResultStatus.SKIPPED;
        }
        return switch (outcome.toLowerCase(Locale.ROOT)) {
            case "passed" -> TestResultStatus.PASSED;
            case "failed", "timeout", "aborted", "error" -> TestResultStatus.FAILED;
            case "blocked" -> TestResultStatus.BLOCKED;
            default -> TestResultStatus.SKIPPED;
        };
    }

    /** The failure text, and for an outcome folded into SKIPPED (Inconclusive, Warning) its name. */
    private static String message(JsonNode result, String outcome, TestResultStatus status) {
        String failure = join(text(result, "errorMessage"), text(result, "stackTrace"));
        boolean folded = status == TestResultStatus.SKIPPED && outcome != null
                && !List.of("notexecuted", "none", "notapplicable").contains(outcome.toLowerCase(Locale.ROOT));
        return folded ? join("Azure DevOps outcome: " + outcome, failure) : failure;
    }

    private static Long durationMs(JsonNode result) {
        JsonNode duration = result.get("durationInMs");
        return duration == null || !duration.isNumber() ? null : Math.round(duration.asDouble());
    }

    private static String join(String first, String second) {
        if (isBlank(first)) {
            return isBlank(second) ? null : second;
        }
        return isBlank(second) ? first : first + "\n" + second;
    }

    private static String firstNonBlank(String first, String second) {
        if (!isBlank(first)) {
            return first;
        }
        return isBlank(second) ? null : second;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }
}
