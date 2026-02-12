package com.deanmanagement.testmanagement.user.internal.requests;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String email,
        @NotBlank String password
) {}
