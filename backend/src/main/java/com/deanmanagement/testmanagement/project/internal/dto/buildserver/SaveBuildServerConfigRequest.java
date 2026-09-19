package com.deanmanagement.testmanagement.project.internal.dto.buildserver;

import com.deanmanagement.testmanagement.project.internal.entity.BuildServerProviderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Create or update a global build-server connection. {@code apiToken} is write-only and optional
 * on update — omitting it keeps the stored token. For Jenkins the token is {@code user:apiToken}.
 */
public record SaveBuildServerConfigRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull BuildServerProviderType provider,
        @NotBlank @Size(max = 500) String baseUrl,
        @Size(max = 500) String apiToken,
        Boolean active,
        /* PRD-026: Azure DevOps only, e.g. 7.1 or 6.0; blank means the default. Ignored for other providers. */
        @Size(max = 10) @Pattern(regexp = "^$|^\\d+\\.\\d+(-preview(\\.\\d+)?)?$",
                message = "must look like 7.1 or 6.0") String apiVersion
) {
}
