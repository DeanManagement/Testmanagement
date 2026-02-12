package com.deanmanagement.testmanagement.user.internal.requests;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String displayName,
        boolean systemAdmin,
        Instant createdAt
) {}
