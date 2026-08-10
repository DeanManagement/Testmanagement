package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.dto.UpdateTestCaseRequest;
import com.deanmanagement.testmanagement.project.internal.dto.filter.TestCaseListFilter;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.TestCaseResponse;
import com.deanmanagement.testmanagement.project.internal.dto.testCaseFolder.MoveTestCasesRequest;
import com.deanmanagement.testmanagement.project.internal.service.TestCaseFolderService;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.project.internal.service.TestCaseService;
import com.deanmanagement.testmanagement.shared.PageableUtils;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Test-case authoring tools (PRD-025 §3.4).
 *
 * <p>Every method resolves its own caller rather than trusting a parameter, so a tool cannot be
 * pointed at another project. Descriptions are written for a model: they spell out enum values
 * verbatim, because an agent that guesses "high" instead of "HIGH" burns a turn on a validation
 * error.
 */
@Service
@RequiredArgsConstructor
public class TestCaseTools {

    private final McpCallerContext callerContext;
    private final TestCaseService testCaseService;
    private final TestCaseFolderService folderService;
    private final TestCaseRepository testCaseRepository;
    private final TestCaseDuplicateDetector duplicateDetector;
    private final McpTestCaseWriter writer;
    private final McpValidator validator;
    private final McpWriteThrottle writeThrottle;
    private final McpProperties properties;

    // --- read ------------------------------------------------------------------------------

    @McpTool(
            name = "search_test_cases",
            description = """
                    Search the project's test cases. All filters are optional; with none you get
                    the most recently updated cases first. Call this BEFORE creating anything —
                    re-creating a case that already exists is the most common mistake here.
                    status: DRAFT | ACTIVE | DEPRECATED. priority: LOW | MEDIUM | HIGH | CRITICAL.
                    Results are paged; check totalElements and hasMore before concluding something
                    does not exist.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    @Transactional(readOnly = true)
    public McpDtos.TestCasePage searchTestCases(
            @McpToolParam(description = "Free-text query over title, key and description", required = false)
            String query,
            @McpToolParam(description = "Only these statuses: DRAFT, ACTIVE, DEPRECATED", required = false)
            List<TestCaseStatus> status,
            @McpToolParam(description = "Only these priorities: LOW, MEDIUM, HIGH, CRITICAL", required = false)
            List<Priority> priority,
            @McpToolParam(description = "Only cases carrying all of these labels", required = false)
            List<String> labels,
            @McpToolParam(description = "Only cases in this folder (see list_test_case_folders)", required = false)
            UUID folderId,
            @McpToolParam(description = "Zero-based page number, default 0", required = false)
            Integer page,
            @McpToolParam(description = "Page size, default 50, max 200", required = false)
            Integer size) {

        var caller = callerContext.require();
        Pageable pageable = pageable(page, size);
        var filter = new TestCaseListFilter(blankToNull(query), status, priority, labels, folderId,
                false, null);

        Page<TestCaseResponse> result =
                testCaseService.findByProject(caller.projectId(), filter, pageable);

        List<McpDtos.TestCaseSummary> summaries = result.getContent().stream()
                .map(tc -> new McpDtos.TestCaseSummary(tc.id(), tc.key(), tc.title(), tc.status(),
                        tc.priority(), tc.labels(), tc.folderId()))
                .toList();
        return new McpDtos.TestCasePage(summaries, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.hasNext());
    }

    @McpTool(
            name = "get_test_case",
            description = """
                    One test case in full, including its ordered steps. Accepts either the case key
                    (for example PROJ-12, which is what humans quote) or its UUID.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    @Transactional(readOnly = true)
    public McpDtos.TestCaseDetail getTestCase(
            @McpToolParam(description = "Test case key (PROJ-12) or UUID") String idOrKey) {
        var caller = callerContext.require();
        TestCase testCase = resolve(caller.projectId(), idOrKey);
        TestCaseResponse response = testCaseService.findById(caller.projectId(), testCase.getId());
        return new McpDtos.TestCaseDetail(
                response.id(), response.key(), response.title(), response.description(),
                response.preconditions(), response.status(), response.priority(), response.labels(),
                response.folderId(),
                response.steps() == null ? List.of() : response.steps().stream()
                        .map(s -> new McpDtos.Step(s.action(), s.expectedResult(), s.testData()))
                        .toList());
    }

    // --- write -----------------------------------------------------------------------------

    @McpTool(
            name = "create_test_case",
            description = """
                    Create a test case. Search first — this refuses titles that already exist and
                    tells you which case to update instead.
                    priority: LOW | MEDIUM | HIGH | CRITICAL (required).
                    status: DRAFT | ACTIVE | DEPRECATED, default DRAFT so a human reviews before the
                    case counts as real.
                    steps: ordered; each has an action, an optional expectedResult and optional
                    testData. Order comes from the array, not from any index you supply.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = false))
    @Transactional
    public McpDtos.CreatedTestCase createTestCase(
            @McpToolParam(description = "Short imperative title, max 255 characters") String title,
            @McpToolParam(description = "LOW, MEDIUM, HIGH or CRITICAL") Priority priority,
            @McpToolParam(description = "What the case covers", required = false) String description,
            @McpToolParam(description = "State the system must be in before the steps", required = false)
            String preconditions,
            @McpToolParam(description = "DRAFT (default), ACTIVE or DEPRECATED", required = false)
            TestCaseStatus status,
            @McpToolParam(description = "Free-form labels", required = false) Set<String> labels,
            @McpToolParam(description = "Ordered steps", required = false) List<McpDtos.Step> steps,
            @McpToolParam(description = "Folder to file it under; omit for the project root", required = false)
            UUID folderId,
            @McpToolParam(description = "Set true only to override a refused duplicate", required = false)
            Boolean allowDuplicateTitle) {

        var caller = callerContext.requireWriter();
        writeThrottle.recordWrite(caller.apiKeyId());
        var index = Boolean.TRUE.equals(allowDuplicateTitle)
                ? null : duplicateDetector.index(caller.projectId());
        return create(caller, title, priority, description, preconditions, status, labels, steps,
                folderId, index);
    }

    @McpTool(
            name = "update_test_case",
            description = """
                    Update a test case. Only the fields you pass are changed; anything you omit
                    keeps its current value. To CLEAR a text field pass an empty string ""; to clear
                    labels or steps pass an empty array.
                    Pass steps only if you mean to replace the whole ordered list — doing so
                    discards any screenshots a human attached to steps that no longer exist.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true))
    @Transactional
    public McpDtos.CreatedTestCase updateTestCase(
            @McpToolParam(description = "Test case key (PROJ-12) or UUID") String idOrKey,
            @McpToolParam(description = "New title; \"\" is rejected, a case must have one", required = false)
            String title,
            @McpToolParam(description = "New description; \"\" clears it", required = false) String description,
            @McpToolParam(description = "New preconditions; \"\" clears them", required = false)
            String preconditions,
            @McpToolParam(description = "LOW, MEDIUM, HIGH or CRITICAL", required = false) Priority priority,
            @McpToolParam(description = "DRAFT, ACTIVE or DEPRECATED", required = false) TestCaseStatus status,
            @McpToolParam(description = "Replaces the label set; [] clears it", required = false)
            Set<String> labels,
            @McpToolParam(description = "Replaces the whole ordered step list; [] clears it", required = false)
            List<McpDtos.Step> steps,
            @McpToolParam(description = "Move the case to this folder", required = false) UUID folderId) {

        var caller = callerContext.requireWriter();
        writeThrottle.recordWrite(caller.apiKeyId());

        TestCase existing = resolve(caller.projectId(), idOrKey);

        // TestCaseService.update null-guards every field, so omitted arguments pass straight
        // through as null and are left alone. It used to assign title/description/preconditions
        // unconditionally, which forced a read-then-merge here — and that made the agent the
        // author of fields it never touched, quietly reverting a human's concurrent edit.
        var request = new UpdateTestCaseRequest(title, description, preconditions, priority, status,
                labels, steps == null ? null : McpTestCaseWriter.toStepRequests(steps));
        validator.validate(request);

        TestCaseResponse updated =
                testCaseService.update(caller.projectId(), existing.getId(), request, caller.userId());

        if (folderId != null) {
            // Folder membership is not part of UpdateTestCaseRequest; it moves separately.
            folderService.moveTestCases(caller.projectId(),
                    new MoveTestCasesRequest(List.of(existing.getId()), folderId), caller.userId());
        }
        return new McpDtos.CreatedTestCase(updated.id(), updated.key(), updated.title(), updated.status());
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
                McpDtos.CreatedTestCase result = create(caller, item.title(), item.priority(),
                        item.description(), item.preconditions(), item.status(), item.labels(),
                        item.steps(), item.folderId(), null);
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
                           @Nullable UUID folderId) {}

    // --- internals -------------------------------------------------------------------------

    /** @param duplicateIndex null to skip the duplicate guard entirely */
    private McpDtos.CreatedTestCase create(McpCallerContext.Caller caller, String title,
                                           Priority priority, String description,
                                           String preconditions, TestCaseStatus status,
                                           Set<String> labels, List<McpDtos.Step> steps,
                                           UUID folderId,
                                           TestCaseDuplicateDetector.Index duplicateIndex) {
        if (priority == null) {
            throw new McpToolException("priority is required: LOW, MEDIUM, HIGH or CRITICAL.");
        }
        if (steps != null && steps.size() > properties.getMaxStepsPerCase()) {
            throw new McpToolException("At most " + properties.getMaxStepsPerCase()
                    + " steps per case; a case needing more is really several cases.");
        }
        if (duplicateIndex != null && title != null) {
            duplicateIndex.find(title).ifPresent(existing -> {
                throw new McpToolException(
                        "A test case with this title already exists: " + existing.key() + " — \""
                                + existing.title() + "\". Call update_test_case on it, or retry "
                                + "with allowDuplicateTitle: true if this really is a separate case.");
            });
        }
        // Its own transaction, so one bad item in a bulk call cannot take the others with it.
        return writer.create(caller.projectId(), caller.userId(), title, priority, description,
                preconditions, status, labels, steps, folderId);
    }

    /**
     * Accepts a key or a UUID, and — this is the part that matters — only ever looks inside the
     * caller's project. A case in another project reports as not-found: to this caller it may as
     * well not exist, and saying "forbidden" would confirm it does (PRD-021 discipline).
     */
    private TestCase resolve(UUID projectId, String idOrKey) {
        if (idOrKey == null || idOrKey.isBlank()) {
            throw new McpToolException("idOrKey is required.");
        }
        String ref = idOrKey.trim();
        try {
            UUID id = UUID.fromString(ref);
            return testCaseRepository.findById(id)
                    .filter(tc -> tc.getProject().getId().equals(projectId))
                    .orElseThrow(() -> new ResourceNotFoundException("TestCase", ref));
        } catch (IllegalArgumentException notAUuid) {
            return testCaseRepository.findByKeyAndProjectId(ref, projectId)
                    .orElseThrow(() -> new ResourceNotFoundException("TestCase", ref));
        }
    }

    private static Pageable pageable(Integer page, Integer size) {
        return PageableUtils.normalize(PageRequest.of(
                page == null || page < 0 ? 0 : page,
                size == null || size < 1 ? PageableUtils.DEFAULT_SIZE : size));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
