package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.entity.SharedStep;
import com.deanmanagement.testmanagement.project.internal.entity.TestStep;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A case's steps as a tester executes them (PRD-030): each reference to a shared block replaced by
 * the block's steps, in order. Everything that runs, records or compares steps goes through here, so
 * a case using a block is never matched against its reference row instead of the block's steps.
 */
public final class StepExpansion {

    private StepExpansion() {
    }

    /** One executable step; {@code block} is the shared block it came from, null for a local step. */
    public record ExpandedStep(TestStep step, SharedStep block) {
    }

    public static List<ExpandedStep> expand(List<TestStep> caseSteps) {
        List<ExpandedStep> expanded = new ArrayList<>();
        for (TestStep step : inOrder(caseSteps)) {
            SharedStep block = step.getUsesSharedStep();
            if (block == null) {
                expanded.add(new ExpandedStep(step, null));
            } else {
                inOrder(block.getSteps()).forEach(blockStep -> expanded.add(new ExpandedStep(blockStep, block)));
            }
        }
        return expanded;
    }

    /** Just the steps, for callers that do not show which block a step came from. */
    public static List<TestStep> expandedSteps(List<TestStep> caseSteps) {
        return expand(caseSteps).stream().map(ExpandedStep::step).toList();
    }

    private static List<TestStep> inOrder(List<TestStep> steps) {
        return steps.stream().sorted(Comparator.comparingInt(TestStep::getOrderIndex)).toList();
    }
}
