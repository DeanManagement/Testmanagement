package com.deanmanagement.testmanagement.user.internal.requests;

import jakarta.validation.constraints.NotBlank;

public record UpdateUserRequest(
        @NotBlank String displayName,
        Boolean systemAdmin,
        String password
) {}
