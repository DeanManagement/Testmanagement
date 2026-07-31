package com.deanmanagement.testmanagement.user.internal.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank String displayName,
        Boolean systemAdmin,
        @Size(min = 8, max = 128) String password
) {}
