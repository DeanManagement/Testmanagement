package com.deanmanagement.testmanagement.project.internal.dto.resultstatus;

import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;

import java.util.UUID;

/** A project's result status (TM24); {@code category} is the fixed outcome every metric counts. */
public record ResultStatusResponse(UUID id, String name, String color, TestResultStatus category, int position,
                                   boolean archived) {
}
