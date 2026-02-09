package com.deanmanagement.testmanagement.user.internal;

import com.deanmanagement.testmanagement.user.UserResponse;

public record LoginResponse(
        String token,
        UserResponse user
) {}
