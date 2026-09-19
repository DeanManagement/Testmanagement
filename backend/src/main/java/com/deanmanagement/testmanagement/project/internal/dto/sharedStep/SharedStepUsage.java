package com.deanmanagement.testmanagement.project.internal.dto.sharedStep;

import java.util.UUID;

/** A test case that references a shared block. */
public record SharedStepUsage(UUID testCaseId, String key, String title) {
}
