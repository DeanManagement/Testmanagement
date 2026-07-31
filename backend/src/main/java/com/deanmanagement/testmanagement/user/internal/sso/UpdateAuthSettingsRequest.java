package com.deanmanagement.testmanagement.user.internal.sso;

import jakarta.validation.constraints.NotNull;

public record UpdateAuthSettingsRequest(@NotNull Boolean localLoginEnabled) {
}
