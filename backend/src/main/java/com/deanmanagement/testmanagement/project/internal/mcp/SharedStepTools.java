package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.dto.sharedStep.SharedStepSummary;
import com.deanmanagement.testmanagement.project.internal.entity.McpToolGroup;
import com.deanmanagement.testmanagement.project.internal.service.SharedStepService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared steps for authoring (PRD-030 §3.5): read-only on purpose. Editing a shared step changes
 * every case that uses it, which is a change a human should make.
 */
@Service
@InToolGroup(McpToolGroup.AUTHORING)
@RequiredArgsConstructor
public class SharedStepTools {

    private static final int PAGE_SIZE = 50;

    private final McpCallerContext callerContext;
    private final SharedStepService sharedStepService;

    @McpTool(
            name = "list_shared_steps",
            description = """
                    The project's shared steps: named blocks of steps kept once and used by many
                    test cases, such as "Log in as admin". To use one in create_test_case or
                    update_test_case, put a step with its sharedStepId where it belongs instead of
                    writing the steps out. query filters by title.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    @Transactional(readOnly = true)
    public McpDtos.SharedStepList listSharedSteps(
            @McpToolParam(description = "Part of the title, case-insensitive", required = false) String query) {
        var caller = callerContext.require();
        Page<SharedStepSummary> page = sharedStepService.list(caller.projectId(), query,
                PageRequest.of(0, PAGE_SIZE, Sort.by("title")));
        return new McpDtos.SharedStepList(page.getContent().stream()
                .map(s -> new McpDtos.SharedStepItem(s.id(), s.title(), s.description(), s.stepCount(), s.usedByCount()))
                .toList(), page.getTotalElements(), page.hasNext());
    }
}
