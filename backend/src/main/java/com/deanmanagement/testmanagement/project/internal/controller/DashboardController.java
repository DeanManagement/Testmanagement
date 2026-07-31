package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.dto.dashboard.DashboardResponse;
import com.deanmanagement.testmanagement.project.internal.service.DashboardService;
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

    @GetMapping
    @RequireProjectRole
    public DashboardResponse getDashboard(@PathVariable UUID projectId) {
        return dashboardService.getDashboard(projectId);
    }
}
