package com.deanmanagement.testmanagement.project.internal.dto.testrun;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SetExecutorRequest(
    @NotNull UUID executorId
) {}
