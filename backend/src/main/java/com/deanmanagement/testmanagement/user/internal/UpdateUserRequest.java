package com.deanmanagement.testmanagement.user.internal;

import jakarta.validation.constraints.NotBlank;

public record UpdateUserRequest(
        @NotBlank String displayName,
        Boolean systemAdmin,
        String password
) {}
