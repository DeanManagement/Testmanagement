package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.service.TestCaseReviewService;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.BulkStatusRequest;
import com.deanmanagement.testmanagement.project.internal.entity.McpToolGroup;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.service.TestCaseService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The many-at-once counterparts of {@link TestCaseTools}. Separate because they share a concern
 * the single-case tools do not have: deciding what a partial failure means, and charging the write
 * budget per item so a batch is not a way around it.
 */
@Service
@InToolGroup(McpToolGroup.AUTHORING)
@RequiredArgsConstructor
public class TestCaseBulkTools {

    private final McpCallerContext callerContext;
    private final TestCaseService testCaseService;
    private final TestCaseDuplicateDetector duplicateDetector;
    private final McpTestCaseCreator creator;
    private final McpValidator validator;
    private final McpWriteThrottle writeThrottle;
    private final McpProperties properties;

    @McpTool(
            name = "change_test_case_status_bulk",
            description = """
                    Set the same status on many test cases at once, up to 100 per call — typically
                    DRAFT to IN_REVIEW once you are done writing, or to DEPRECATED for cases that no
                    longer apply. status: DRAFT | IN_REVIEW | ACTIVE | DEPRECATED. In projects that
                    require review, ACTIVE means approved: only a human reviewer can set it, so use
                    IN_REVIEW.
                    Every id must name a test case in this project; if one does not, nothing is
                    changed. For a single case use update_test_case.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true))
    @Transactional
    public McpDtos.BulkStatusResult changeTestCaseStatusBulk(
            @McpToolParam(description = "UUIDs of the test cases to change") Set<UUID> testCaseIds,
            @McpToolParam(description = "DRAFT, IN_REVIEW, ACTIVE or DEPRECATED") TestCaseStatus status) {

        var caller = callerContext.requireWriter();
        var request = new BulkStatusRequest(testCaseIds, status);
        validator.validate(request);
        // One write per case, as record_test_results charges one per result.
        writeThrottle.recordWrites(caller.apiKeyId(), testCaseIds.size());

        try {
            int updated = testCaseService.bulkUpdateStatus(caller.projectId(), request,
                    caller.userId()).affected();
            return new McpDtos.BulkStatusResult(updated, status);
        } catch (TestCaseReviewService.ReviewRequiredException reviewRequired) {
            throw new McpToolException(reviewRequired.getMessage());
        } catch (IllegalArgumentException unknownIds) {
            throw new McpToolException("At least one id does not name a test case in this "
                    + "project, so nothing was changed. Check them with search_test_cases.");
        }
    }

    @McpTool(
            name = "create_test_cases_bulk",
            description = """
                    Create several test cases in one call. Each item is created independently, so a
                    partial result is normal: read the per-item outcome (CREATED, SKIPPED for a
                    duplicate title, or ERROR) rather than assuming all-or-nothing. Use dryRun to
                    see what would happen without writing anything.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = false))
    // Deliberately NOT @Transactional: each item commits on its own via McpTestCaseWriter. A
    // shared transaction here would mark itself rollback-only on the first failure and take every
    // "created" item down with it at commit — after the tool had already reported them created.
    public McpDtos.BulkResult createTestCasesBulk(
            @McpToolParam(description = "The cases to create") List<BulkCase> cases,
            @McpToolParam(description = "Validate only, write nothing", required = false) Boolean dryRun) {

        var caller = callerContext.requireWriter();
        if (cases == null || cases.isEmpty()) {
            throw new McpToolException("No cases supplied.");
        }
        if (cases.size() > properties.getMaxBulkSize()) {
            throw new McpToolException("At most " + properties.getMaxBulkSize()
                    + " cases per call; you sent " + cases.size() + ". Split the batch.");
        }
        boolean dry = Boolean.TRUE.equals(dryRun);
        if (!dry) {
            writeThrottle.recordWrites(caller.apiKeyId(), cases.size());
        }

        // Loaded once, and updated as we go so a batch that repeats a title within itself is
        // caught too — an agent generating 50 cases from one prompt does that more often than it
        // collides with the existing project.
        var index = duplicateDetector.index(caller.projectId());

        List<McpDtos.BulkItemResult> results = new ArrayList<>();
        int created = 0;
        int skipped = 0;
        int failed = 0;
        for (int i = 0; i < cases.size(); i++) {
            BulkCase item = cases.get(i);
            try {
                var duplicate = index.find(item.title());
                if (duplicate.isPresent()) {
                    results.add(new McpDtos.BulkItemResult(i, "SKIPPED", duplicate.get().id(),
                            duplicate.get().key(),
                            "A test case with this title already exists; update it instead"));
                    skipped++;
                    continue;
                }
                if (dry) {
                    results.add(new McpDtos.BulkItemResult(i, "CREATED", null, null,
                            "Would be created (dry run)"));
                    index.remember(item.title(),
                            new TestCaseDuplicateDetector.Existing(null, null, item.title()));
                    created++;
                    continue;
                }
                McpDtos.CreatedTestCase result = creator.create(caller, item.title(), item.priority(),
                        item.description(), item.preconditions(), item.status(), item.labels(),
                        item.steps(), item.folderId(), null, item.estimateMinutes(), null);
                index.remember(result.title(), new TestCaseDuplicateDetector.Existing(
                        result.id(), result.key(), result.title()));
                results.add(new McpDtos.BulkItemResult(i, "CREATED", result.id(), result.key(), null));
                created++;
            } catch (RuntimeException e) {
                results.add(new McpDtos.BulkItemResult(i, "ERROR", null, null, e.getMessage()));
                failed++;
            }
        }
        return new McpDtos.BulkResult(dry, created, skipped, failed, results);
    }

    /**
     * One item of {@code create_test_cases_bulk}; mirrors {@code create_test_case}'s arguments.
     *
     * <p>Everything but title and priority is {@code @Nullable} so the generated schema marks it
     * optional — see {@link McpDtos.Step} for why that annotation is required rather than cosmetic.
     * Without it an agent has to send all eight fields on every one of fifty items.
     */
    public record BulkCase(String title,
                           Priority priority,
                           @Nullable String description,
                           @Nullable String preconditions,
                           @Nullable TestCaseStatus status,
                           @Nullable Set<String> labels,
                           @Nullable List<McpDtos.Step> steps,
                           @Nullable UUID folderId,
                           @Nullable Integer estimateMinutes) {}
}
