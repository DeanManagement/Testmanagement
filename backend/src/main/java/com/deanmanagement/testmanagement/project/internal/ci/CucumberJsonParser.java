package com.deanmanagement.testmanagement.project.internal.ci;

import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses standard Cucumber JSON (array of features) into normalized {@link CiResult}s. Each scenario
 * becomes a result; its steps become {@link CiResult.CiStep}s. A scenario fails if any step fails.
 */
@Component
@RequiredArgsConstructor
public class CucumberJsonParser {

    private final ObjectMapper objectMapper;

    public List<CiResult> parse(byte[] json) {
        Feature[] features;
        try {
            features = objectMapper.readValue(json, Feature[].class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Cucumber JSON: " + e.getMessage());
        }
        if (features == null || features.length == 0) {
            throw new IllegalArgumentException("No features found in Cucumber JSON");
        }

        List<CiResult> results = new ArrayList<>();
        for (Feature feature : features) {
            String featureName = feature.name() != null ? feature.name() : "Feature";
            if (feature.elements() == null) {
                continue;
            }
            for (Element element : feature.elements()) {
                if (element.type() != null && !element.type().equalsIgnoreCase("scenario")) {
                    continue; // skip backgrounds etc.
                }
                results.add(toResult(featureName, element));
            }
        }
        if (results.isEmpty()) {
            throw new IllegalArgumentException("No scenarios found in Cucumber JSON");
        }
        return results;
    }

    private CiResult toResult(String featureName, Element element) {
        String title = featureName + " - " + (element.name() != null ? element.name() : "Scenario");
        List<CiResult.CiStep> steps = new ArrayList<>();
        StringBuilder failureMessage = new StringBuilder();
        boolean anyFailed = false;
        boolean anyPassed = false;

        if (element.steps() != null) {
            for (Step step : element.steps()) {
                String stepStatus = step.result() != null ? step.result().status() : null;
                TestResultStatus mapped = mapStatus(stepStatus);
                if (mapped == TestResultStatus.FAILED) {
                    anyFailed = true;
                    if (step.result() != null && step.result().errorMessage() != null) {
                        failureMessage.append(step.result().errorMessage()).append('\n');
                    }
                }
                if (mapped == TestResultStatus.PASSED) {
                    anyPassed = true;
                }
                String stepName = (step.keyword() != null ? step.keyword() : "")
                        + (step.name() != null ? step.name() : "");
                steps.add(new CiResult.CiStep(stepName.trim(), mapped));
            }
        }

        TestResultStatus status;
        if (anyFailed) {
            status = TestResultStatus.FAILED;
        } else if (anyPassed) {
            status = TestResultStatus.PASSED;
        } else {
            status = TestResultStatus.SKIPPED;
        }

        String message = failureMessage.isEmpty() ? null : failureMessage.toString().trim();
        return new CiResult(featureName, title, status, message, steps);
    }

    private TestResultStatus mapStatus(String status) {
        if (status == null) {
            return TestResultStatus.PENDING;
        }
        return switch (status.toLowerCase()) {
            case "passed" -> TestResultStatus.PASSED;
            case "failed" -> TestResultStatus.FAILED;
            case "skipped" -> TestResultStatus.SKIPPED;
            default -> TestResultStatus.BLOCKED; // pending, undefined, ambiguous
        };
    }

    private record Feature(String name, List<Element> elements) {
    }

    private record Element(String name, String type, List<Step> steps) {
    }

    private record Step(String keyword, String name, Result result) {
    }

    private record Result(String status, @JsonProperty("error_message") String errorMessage) {
    }
}
