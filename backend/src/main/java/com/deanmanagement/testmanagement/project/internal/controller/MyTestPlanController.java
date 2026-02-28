package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.dto.testplan.TestPlanResponse;
import com.deanmanagement.testmanagement.project.internal.service.TestPlanService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/test-plans")
@Tag(name = "My Test Plans", description = "Current user test plan endpoints")
@RequiredArgsConstructor
public class MyTestPlanController {

    private final TestPlanService testPlanService;

    @GetMapping("/assigned-to-me")
    public List<TestPlanResponse> getAssignedToMe(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return testPlanService.findByAssignee(userId);
    }
}
