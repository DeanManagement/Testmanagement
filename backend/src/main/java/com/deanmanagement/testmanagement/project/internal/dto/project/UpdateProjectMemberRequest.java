package com.deanmanagement.testmanagement.project.internal.dto.project;

import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import jakarta.validation.constraints.NotNull;

public record UpdateProjectMemberRequest(
        @NotNull ProjectRole role
) {}
