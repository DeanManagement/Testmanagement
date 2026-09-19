package com.deanmanagement.testmanagement.project.internal.dto.report;

/**
 * What the run report PDF includes besides the results (PRD-048). Screenshots sit in the step
 * table, so asking for them includes the steps.
 */
public record RunReportOptions(boolean steps, boolean screenshots) {

    public static final RunReportOptions RESULTS_ONLY = new RunReportOptions(false, false);

    public boolean includesSteps() {
        return steps || screenshots;
    }
}
