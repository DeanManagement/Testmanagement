package com.deanmanagement.testmanagement.project.internal.dto.testrun;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

/** TM22: cases to add to an existing run; ones already in it are left as they are. */
public record AddRunTestCasesRequest(@NotNull @Size(min = 1, max = 200) Set<UUID> testCaseIds) {
}
