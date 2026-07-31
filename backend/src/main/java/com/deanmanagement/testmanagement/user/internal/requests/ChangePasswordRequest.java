package com.deanmanagement.testmanagement.user.internal.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters") String newPassword
) {}
