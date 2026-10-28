package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.dto.sharedStep.SaveSharedStepRequest;
import com.deanmanagement.testmanagement.project.internal.dto.sharedStep.SharedStepResponse;
import com.deanmanagement.testmanagement.project.internal.dto.sharedStep.SharedStepStepRequest;
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

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Shared steps for authoring (PRD-030 §3.5). Read-only at first, because editing one changes every
 * case that uses it; the product owner has since asked for agents to maintain them too (TM14). The
 * descriptions carry that warning instead, and {@link SharedStepService} applies the same safeguards
 * as for a human: affected cases get a version first and go back to review, and a block still in use
 * cannot be deleted.
 */
@Service
@InToolGroup(McpToolGroup.AUTHORING)
@RequiredArgsConstructor
public class SharedStepTools {

    private static final int PAGE_SIZE = 50;

    private final McpCallerContext callerContext;
    private final SharedStepService sharedStepService;
    private final McpWriteThrottle writeThrottle;
    private final McpValidator validator;

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

    @McpTool(
            name = "get_shared_step",
            description = """
                    One shared step with its steps in order, each with its id, and usedByCount: how
                    many test cases use it. Read this before update_shared_step and send the ids
                    back for the steps you keep.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    @Transactional(readOnly = true)
    public McpDtos.SharedStepDetail getSharedStep(
            @McpToolParam(description = "Shared step UUID from list_shared_steps") UUID id) {
        var caller = callerContext.require();
        return detail(sharedStepService.get(caller.projectId(), id));
    }

    @McpTool(
            name = "create_shared_step",
            description = """
                    Create a shared step: a named block of steps, such as "Log in as admin", that
                    test cases then use by sharedStepId instead of repeating the steps. The title
                    must be unique in the project. steps: ordered, each with an action and an
                    optional expectedResult and testData; leave id out.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = false))
    @Transactional
    public McpDtos.SharedStepDetail createSharedStep(
            @McpToolParam(description = "Title, max 255 characters, unique in the project") String title,
            @McpToolParam(description = "Ordered steps") List<McpDtos.SharedStepStep> steps,
            @McpToolParam(description = "What the block is for", required = false) String description) {

        var caller = callerContext.requireWriter();
        writeThrottle.recordWrite(caller.apiKeyId());
        var request = new SaveSharedStepRequest(title, description, toRequests(steps));
        validator.validate(request);
        return detail(sharedStepService.create(caller.projectId(), request, caller.userId()));
    }

    @McpTool(
            name = "update_shared_step",
            description = """
                    Change a shared step's title, description or steps. This changes EVERY test case
                    that uses it (usedByCount from get_shared_step): when the steps change, each of
                    those cases gets a new version, and approved ones go back to review.
                    Only the fields you pass are changed; to CLEAR the description pass "".
                    steps replaces the whole ordered list: send every step the block should keep,
                    with the id get_shared_step gave it, so results already recorded against that
                    step keep their text. A step without an id is new; a step you leave out is
                    removed.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true))
    @Transactional
    public McpDtos.SharedStepDetail updateSharedStep(
            @McpToolParam(description = "Shared step UUID") UUID id,
            @McpToolParam(description = "New title, max 255 characters", required = false) String title,
            @McpToolParam(description = "New description; \"\" clears it", required = false)
            String description,
            @McpToolParam(description = "The complete new ordered step list", required = false)
            List<McpDtos.SharedStepStep> steps) {

        var caller = callerContext.requireWriter();
        writeThrottle.recordWrite(caller.apiKeyId());
        if (title == null && description == null && steps == null) {
            throw new McpToolException("Nothing to update: pass title, description or steps.");
        }

        // Merged here because the service is a full replace; safe only while this method stays
        // @Transactional — see TestPlanningMaintenanceTools.
        SharedStepResponse current = sharedStepService.get(caller.projectId(), id);
        var request = new SaveSharedStepRequest(
                title == null ? current.title() : title,
                description == null ? current.description() : emptyToNull(description),
                steps == null ? detail(current).steps().stream()
                        .map(SharedStepTools::toRequest).toList() : toRequests(steps));
        validator.validate(request);
        return detail(sharedStepService.update(caller.projectId(), id, request, caller.userId()));
    }

    @McpTool(
            name = "delete_shared_step",
            description = """
                    Permanently delete a shared step. Refused while any test case still uses it —
                    those cases would silently lose steps; update them first. Cannot be undone.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = true, idempotentHint = false))
    @Transactional
    public McpDtos.Deleted deleteSharedStep(@McpToolParam(description = "Shared step UUID") UUID id) {
        var caller = callerContext.requireWriter();
        writeThrottle.recordWrite(caller.apiKeyId());
        String title = sharedStepService.get(caller.projectId(), id).title();
        sharedStepService.delete(caller.projectId(), id, caller.userId());
        return new McpDtos.Deleted("SHARED_STEP", id, title);
    }

    private static List<SharedStepStepRequest> toRequests(List<McpDtos.SharedStepStep> steps) {
        if (steps == null) {
            throw new McpToolException("steps is required: an ordered list, each with an action.");
        }
        return steps.stream().map(SharedStepTools::toRequest).toList();
    }

    private static SharedStepStepRequest toRequest(McpDtos.SharedStepStep step) {
        return new SharedStepStepRequest(step.id(), step.action(), step.expectedResult(), step.testData());
    }

    private static McpDtos.SharedStepDetail detail(SharedStepResponse block) {
        List<McpDtos.SharedStepStep> steps = block.steps() == null ? List.of() : block.steps().stream()
                .sorted(Comparator.comparingInt(s -> s.orderIndex()))
                .map(s -> new McpDtos.SharedStepStep(s.id(), s.action(), s.expectedResult(), s.testData()))
                .toList();
        return new McpDtos.SharedStepDetail(block.id(), block.title(), block.description(),
                block.usedByCount(), steps);
    }

    private static String emptyToNull(String value) {
        return value.isBlank() ? null : value;
    }
}
