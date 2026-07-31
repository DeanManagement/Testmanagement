package com.deanmanagement.testmanagement.project.internal.dto.ci;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record CiTriggerRequest(
    @NotBlank String provider, // "WOODPECKER" or "AZURE"
    @NotBlank String pipelineId,
    Map<String,String> variables
) {}
