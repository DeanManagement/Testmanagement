package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.dto.myqueue.MyQueueResponse;
import com.deanmanagement.testmanagement.project.internal.service.MyQueueService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * "My queue" dashboard widget — aggregates due test plans, in-progress
 * test runs, stale bug reports, and long-lived draft test cases for the
 * calling user into a single small payload. Backed by {@link MyQueueService}.
 *
 * <p>The endpoint is intentionally side-effect free and uncached: the user
 * is the only person looking at it, and freshness matters more than load.
 */
@RestController
@RequestMapping("/api/me")
@Tag(name = "My Queue", description = "Personal dashboard widget aggregating work that needs the user's attention")
@RequiredArgsConstructor
public class MyQueueController {

    private final MyQueueService myQueueService;

    @GetMapping("/queue")
    public MyQueueResponse getMyQueue(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return myQueueService.buildFor(userId);
    }
}
