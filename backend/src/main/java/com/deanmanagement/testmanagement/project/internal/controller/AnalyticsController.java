package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.access.RequireProjectRole;
import com.deanmanagement.testmanagement.project.internal.dto.analytics.FlakyTestResponse;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.service.FlakyTestService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Project analytics over existing result history (PRD-016). Read access matches the rest of the
 * project: any member may look, membership is enforced by the aspect.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/analytics")
@Tag(name = "Analytics", description = "Derived project metrics")
@RequiredArgsConstructor
public class AnalyticsController {

    private static final int MAX_LIMIT = 50;

    private final FlakyTestService flakyTestService;

    /** Top flaky cases. Only cases that pass the threshold and min-runs bar are returned. */
    @GetMapping("/flaky")
    @RequireProjectRole
    public List<FlakyTestResponse> flaky(@PathVariable UUID projectId,
                                         @RequestParam(defaultValue = "10") int limit) {
        return flakyTestService.findFlaky(projectId, Math.min(limit, MAX_LIMIT));
    }

    /**
     * Applies the flaky label to match current scoring. A no-op unless {@code app.flaky.auto-label}
     * is on, and admin-only because it edits test cases and writes audit entries.
     */
    @PostMapping("/flaky/sync-labels")
    @RequireProjectRole(ProjectRole.ADMIN)
    public Map<String, Integer> syncLabels(@PathVariable UUID projectId, Authentication authentication) {
        UUID actorId = authentication != null ? UUID.fromString(authentication.getName()) : null;
        return Map.of("updated", flakyTestService.syncLabels(projectId, actorId));
    }
}
