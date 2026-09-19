package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.entity.McpToolGroup;
import com.deanmanagement.testmanagement.project.internal.dto.UpdateTestCaseRequest;
import com.deanmanagement.testmanagement.project.internal.dto.filter.TestCaseListFilter;
import com.deanmanagement.testmanagement.project.internal.dto.TestStepResponse;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.TestCaseResponse;
import com.deanmanagement.testmanagement.project.internal.dto.testCaseFolder.MoveTestCasesRequest;
import com.deanmanagement.testmanagement.project.internal.service.TestCaseFolderService;
import com.deanmanagement.testmanagement.project.internal.entity.CustomFieldEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.project.internal.service.CustomFieldFilterParser;
import com.deanmanagement.testmanagement.project.internal.service.CustomFieldWriteMode;
import com.deanmanagement.testmanagement.project.internal.service.TestCaseService;
import com.deanmanagement.testmanagement.shared.PageableUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Stream;
import java.util.Map;
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
@InToolGroup(McpToolGroup.AUTHORING)
@RequiredArgsConstructor
public class TestCaseTools {

    private final McpCallerContext callerContext;
    private final TestCaseService testCaseService;
    private final TestCaseFolderService folderService;
    private final TestCaseRepository testCaseRepository;
    private final TestCaseDuplicateDetector duplicateDetector;
    private final McpTestCaseCreator creator;
    private final McpValidator validator;
    private final McpWriteThrottle writeThrottle;
    private final CustomFieldFilterParser customFieldFilterParser;

    // --- read ------------------------------------------------------------------------------

    @McpTool(
            name = "search_test_cases",
            description = """
                    Search the project's test cases. All filters are optional; with none you get
                    the most recently updated cases first. Call this BEFORE creating anything —
                    re-creating a case that already exists is the most common mistake here.
                    status: DRAFT | IN_REVIEW | ACTIVE | DEPRECATED. priority: LOW | MEDIUM | HIGH | CRITICAL.
                    Results are paged; check totalElements and hasMore before concluding something
                    does not exist.
                    customFields: by field NAME (see list_custom_fields), e.g. {"Component": "Checkout"};
                    a list value matches any of its entries; a TEXT field matches a substring.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    @Transactional(readOnly = true)
    public McpDtos.TestCasePage searchTestCases(
            @McpToolParam(description = "Free-text query over title, key and description", required = false)
            String query,
            @McpToolParam(description = "Only these statuses: DRAFT, IN_REVIEW, ACTIVE, DEPRECATED", required = false)
            List<TestCaseStatus> status,
            @McpToolParam(description = "Only these priorities: LOW, MEDIUM, HIGH, CRITICAL", required = false)
            List<Priority> priority,
            @McpToolParam(description = "Only cases carrying all of these labels", required = false)
            List<String> labels,
            @McpToolParam(description = "Only cases in this folder (see list_test_case_folders)", required = false)
            UUID folderId,
            @McpToolParam(description = "With folderId: also include cases in its subfolders, default false",
                    required = false)
            Boolean includeSubfolders,
            @McpToolParam(description = "Only cases whose custom fields match, keyed by field name",
                    required = false) Map<String, Object> customFields,
            @McpToolParam(description = "Zero-based page number, default 0", required = false)
            Integer page,
            @McpToolParam(description = "Page size, default 50, max 200", required = false)
            Integer size) {

        var caller = callerContext.require();
        Pageable pageable = pageable(page, size);
        var filter = new TestCaseListFilter(blankToNull(query), status, priority, labels, folderId,
                Boolean.TRUE.equals(includeSubfolders), false, null,
                customFieldFilterParser.parse(caller.projectId(), CustomFieldEntityType.TEST_CASE,
                        asFilterParams(customFields)));

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
                    One test case in full, including its ordered steps, its estimateMinutes and
                    medianActualMs (median measured duration of its last 5 executions). Steps are
                    as executed: a shared step's steps appear in place, each with its sharedStepId
                    and sharedStepTitle. Accepts either the case key
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
                response.steps() == null ? List.of() : executedSteps(response.steps()),
                response.customFields(), response.estimateMinutes(), response.medianActualMs());
    }

    /** Shared steps expanded, each of their steps tagged with the shared step (PRD-030). */
    private static List<McpDtos.Step> executedSteps(List<TestStepResponse> steps) {
        return steps.stream()
                .flatMap(s -> s.sharedStepId() == null
                        ? Stream.of(new McpDtos.Step(s.action(), s.expectedResult(), s.testData()))
                        : s.expandedSteps().stream().map(b -> new McpDtos.Step(b.action(), b.expectedResult(),
                                b.testData(), s.sharedStepId(), s.sharedStepTitle())))
                .toList();
    }

    // --- write -----------------------------------------------------------------------------

    @McpTool(
            name = "create_test_case",
            description = """
                    Create a test case. Search first — this refuses titles that already exist and
                    tells you which case to update instead.
                    priority: LOW | MEDIUM | HIGH | CRITICAL (required).
                    status: DRAFT | IN_REVIEW | ACTIVE | DEPRECATED, default DRAFT so a human reviews
                    before the case counts as real. In projects that require review ACTIVE means
                    approved and is refused here: use IN_REVIEW.
                    steps: ordered; each has an action, an optional expectedResult and optional
                    testData, or instead a sharedStepId (see list_shared_steps) to use a project's
                    shared step there. Order comes from the array, not from any index you supply.
                    customFields: keyed by field NAME, not id — call list_custom_fields for names,
                    types and options. MULTI_SELECT takes an array; DATE is yyyy-MM-dd.
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
            @McpToolParam(description = "DRAFT (default), IN_REVIEW, ACTIVE or DEPRECATED", required = false)
            TestCaseStatus status,
            @McpToolParam(description = "Free-form labels", required = false) Set<String> labels,
            @McpToolParam(description = "Ordered steps", required = false) List<McpDtos.Step> steps,
            @McpToolParam(description = "Folder to file it under; omit for the project root", required = false)
            UUID folderId,
            @McpToolParam(description = "Custom field values keyed by field name", required = false)
            Map<String, Object> customFields,
            @McpToolParam(description = "Expected minutes for one execution, 1-1440", required = false)
            Integer estimateMinutes,
            @McpToolParam(description = "Set true only to override a refused duplicate", required = false)
            Boolean allowDuplicateTitle) {

        var caller = callerContext.requireWriter();
        writeThrottle.recordWrite(caller.apiKeyId());
        var index = Boolean.TRUE.equals(allowDuplicateTitle)
                ? null : duplicateDetector.index(caller.projectId());
        return creator.create(caller, title, priority, description, preconditions, status, labels,
                steps, folderId, customFields, estimateMinutes, index);
    }

    @McpTool(
            name = "update_test_case",
            description = """
                    Update a test case. Only the fields you pass are changed; anything you omit
                    keeps its current value. To CLEAR a text field pass an empty string ""; to clear
                    labels or steps pass an empty array.
                    Pass steps only if you mean to replace the whole ordered list — doing so
                    discards any screenshots a human attached to steps that no longer exist.
                    customFields: keyed by field NAME, not id; only the names you pass change, and
                    a null value clears that field.
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
            @McpToolParam(description = "DRAFT, IN_REVIEW, ACTIVE or DEPRECATED", required = false) TestCaseStatus status,
            @McpToolParam(description = "Replaces the label set; [] clears it", required = false)
            Set<String> labels,
            @McpToolParam(description = "Replaces the whole ordered step list; [] clears it", required = false)
            List<McpDtos.Step> steps,
            @McpToolParam(description = "Move the case to this folder", required = false) UUID folderId,
            @McpToolParam(description = "Custom field values keyed by field name; null clears one",
                    required = false) Map<String, Object> customFields,
            @McpToolParam(description = "Expected minutes for one execution, 1-1440; 0 clears it", required = false)
            Integer estimateMinutes) {

        var caller = callerContext.requireWriter();
        writeThrottle.recordWrite(caller.apiKeyId());

        TestCase existing = resolve(caller.projectId(), idOrKey);

        // TestCaseService.update null-guards every field, so omitted arguments pass straight
        // through as null and are left alone. It used to assign title/description/preconditions
        // unconditionally, which forced a read-then-merge here — and that made the agent the
        // author of fields it never touched, quietly reverting a human's concurrent edit.
        var request = new UpdateTestCaseRequest(title, description, preconditions, priority, status,
                labels, steps == null ? null : McpTestCaseWriter.toStepRequests(steps), customFields,
                estimateMinutes);
        validator.validate(request);

        TestCaseResponse updated = testCaseService.update(caller.projectId(), existing.getId(), request,
                caller.userId(), CustomFieldWriteMode.MACHINE);

        if (folderId != null) {
            // Folder membership is not part of UpdateTestCaseRequest; it moves separately.
            folderService.moveTestCases(caller.projectId(),
                    new MoveTestCasesRequest(List.of(existing.getId()), folderId), caller.userId());
        }
        return new McpDtos.CreatedTestCase(updated.id(), updated.key(), updated.title(), updated.status());
    }

    // --- internals -------------------------------------------------------------------------

    /**
     * Accepts a key or a UUID, and — this is the part that matters — only ever looks inside the
     * caller's project. A case in another project reports as not-found: to this caller it may as
     * well not exist, and saying "forbidden" would confirm it does (PRD-021 discipline).
     */
    private TestCase resolve(UUID projectId, String idOrKey) {
        return McpTestCaseReferences.resolve(testCaseRepository, projectId, idOrKey);
    }

    private static Pageable pageable(Integer page, Integer size) {
        return PageableUtils.normalize(PageRequest.of(
                page == null || page < 0 ? 0 : page,
                size == null || size < 1 ? PageableUtils.DEFAULT_SIZE : size));
    }

    /** An equality map as the {@code cf.<name>} parameters the list endpoint takes. */
    private static Map<String, List<String>> asFilterParams(Map<String, Object> customFields) {
        Map<String, List<String>> params = new LinkedHashMap<>();
        if (customFields != null) {
            customFields.forEach((name, value) -> {
                if (value instanceof Collection<?> values) {
                    params.put(CustomFieldFilterParser.PARAM_PREFIX + name, values.stream().map(String::valueOf).toList());
                } else if (value != null) {
                    params.put(CustomFieldFilterParser.PARAM_PREFIX + name, List.of(value.toString()));
                }
            });
        }
        return params;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
