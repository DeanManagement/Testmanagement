package com.deanmanagement.testmanagement.project.internal.dto.attachment;

import java.util.UUID;

/** Which step a file is on, without the file (TM05): enough to move it when the step is replaced. */
public record StepFileRef(UUID id, UUID testStepId) {
}
