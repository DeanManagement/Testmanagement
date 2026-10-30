package com.deanmanagement.testmanagement.project.internal.dto.bugstatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** TM47: the project's whole transition table; empty allows any move again. */
public record BugStatusTransitionsRequest(@NotNull @Size(max = 2500) List<@Valid @NotNull Transition> transitions) {

    public record Transition(@NotNull UUID fromStatusId, @NotNull UUID toStatusId) {
    }
}
