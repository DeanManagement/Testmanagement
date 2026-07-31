package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.dto.ci.CiTriggerRequest;
import com.deanmanagement.testmanagement.project.internal.service.CiTriggerService;
import com.deanmanagement.testmanagement.project.internal.access.RequireProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/ci")
@Tag(name = "CI Integration", description = "Trigger external CI pipelines (Woodpecker, Azure DevOps, etc.)")
@RequiredArgsConstructor
public class CiIntegrationController {

    private final CiTriggerService ciTriggerService;

    @PostMapping("/trigger")
    @RequireProjectRole(ProjectRole.TESTER)
    @ResponseStatus(HttpStatus.OK)
    public String triggerPipeline(@PathVariable UUID projectId,
                                  @Valid @RequestBody CiTriggerRequest request) throws IOException {
        // Very simple mapping – real code would resolve provider + pipeline URL from DB or config.
        String url = resolveUrl(request.provider(), request.pipelineId());
        return ciTriggerService.trigger(url, "<TOKEN>"); // token placeholder – replace with secret handling
    }

    private String resolveUrl(String provider, String pipelineId) {
        return switch (provider.toUpperCase()) {
            case "WOODPECKER" -> "https://ci.example.com/api/v1/repos/your-repo/pipelines/" + pipelineId + "/start";
            case "AZURE" -> "https://dev.azure.com/your-org/_apis/pipelines/" + pipelineId + "/runs?api-version=6.0-preview.1";
            default -> throw new IllegalArgumentException("Unsupported CI provider: " + provider);
        };
    }
}
