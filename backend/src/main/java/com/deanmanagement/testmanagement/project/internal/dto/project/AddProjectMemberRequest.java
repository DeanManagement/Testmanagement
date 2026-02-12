package com.deanmanagement.testmanagement.project.internal.dto.project;

import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddProjectMemberRequest(
        @NotNull UUID userId,
        @NotNull ProjectRole role
) {}
