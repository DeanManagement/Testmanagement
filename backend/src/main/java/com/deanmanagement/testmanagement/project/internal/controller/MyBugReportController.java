package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.dto.bugReport.BugReportResponse;
import com.deanmanagement.testmanagement.project.internal.service.BugReportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/bug-reports")
@Tag(name = "My Bug Reports", description = "Current user bug report endpoints")
@RequiredArgsConstructor
public class MyBugReportController {

    private final BugReportService bugReportService;

    @GetMapping("/assigned-to-me")
    public List<BugReportResponse> getAssignedToMe(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return bugReportService.findByAssignee(userId);
    }
}
