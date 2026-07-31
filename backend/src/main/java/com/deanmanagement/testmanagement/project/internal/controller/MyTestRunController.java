package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.dto.TestRunSummaryResponse;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import com.deanmanagement.testmanagement.project.internal.service.TestRunService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/test-runs")
@Tag(name = "My Test Runs", description = "Current user test run endpoints")
@RequiredArgsConstructor
public class MyTestRunController {

    private final TestRunService testRunService;

    @GetMapping("/assigned-to-me")
    public List<TestRunSummaryResponse> getAssignedToMe(
            Authentication authentication,
            @RequestParam(required = false) List<TestRunStatus> statuses) {
        UUID userId = UUID.fromString(authentication.getName());
        if (statuses != null && !statuses.isEmpty()) {
            return testRunService.findByExecutorWithStatuses(userId, statuses);
        }
        return testRunService.findByExecutor(userId);
    }
}
