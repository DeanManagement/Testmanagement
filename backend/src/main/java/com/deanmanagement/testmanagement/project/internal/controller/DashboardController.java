package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.dto.dashboard.DashboardResponse;
import com.deanmanagement.testmanagement.project.internal.service.DashboardService;
import com.deanmanagement.testmanagement.project.internal.service.DefectDashboardService;
import com.deanmanagement.testmanagement.project.internal.dto.dashboard.DefectDashboardResponse;
import com.deanmanagement.testmanagement.project.internal.access.RequireProjectRole;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/dashboard")
@Tag(name = "Dashboard", description = "Project dashboard data endpoints")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final DefectDashboardService defectDashboardService;

    @GetMapping
    @RequireProjectRole
    public DashboardResponse getDashboard(@PathVariable UUID projectId) {
        return dashboardService.getDashboard(projectId);
    }

    /** PRD-047: open bugs by status and priority, and reported versus resolved per week. */
    @GetMapping("/defects")
    @RequireProjectRole
    public DefectDashboardResponse getDefects(@PathVariable UUID projectId) {
        return defectDashboardService.defects(projectId);
    }
}
