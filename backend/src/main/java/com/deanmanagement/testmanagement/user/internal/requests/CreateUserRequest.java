package com.deanmanagement.testmanagement.user.internal.requests;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Email String email,
        @NotBlank String displayName,
        @NotBlank @Size(min = 8) String password,
        boolean systemAdmin
) {}
