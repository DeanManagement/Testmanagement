package com.deanmanagement.testmanagement.user.internal.requests;

public record LoginResponse(
        String token,
        UserResponse user
) {}
