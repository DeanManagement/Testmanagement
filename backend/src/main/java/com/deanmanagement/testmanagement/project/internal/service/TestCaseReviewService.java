package com.deanmanagement.testmanagement.project.internal.service;

import java.util.Set;
import java.util.HashSet;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseVersionRepository;
import com.deanmanagement.testmanagement.project.internal.entity.TestResult;
import com.deanmanagement.testmanagement.project.internal.access.ProjectAccessService;
import com.deanmanagement.testmanagement.project.internal.dto.TestStepRequest;
import com.deanmanagement.testmanagement.project.internal.dto.UpdateTestCaseRequest;
import com.deanmanagement.testmanagement.project.internal.dto.comment.CreateCommentRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.ReviewCapabilitiesResponse;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.TestCaseMapper;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.TestCaseResponse;
import com.deanmanagement.testmanagement.project.internal.entity.AuditAction;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.CommentEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestStep;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.shared.exception.ConflictException;
import com.deanmanagement.testmanagement.shared.exception.ForbiddenException;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Test case review and approval (PRD-033). Every path that writes a case's status calls
 * {@link #checkStatusWrite}, which is what makes "ACTIVE means approved" hold in projects with
 * review switched on; projects without it behave exactly as before.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TestCaseReviewService {

    static final String USE_APPROVE =
            "This project requires review: set the case to IN_REVIEW and have another member approve it";

    private final TestCaseRepository testCaseRepository;
    private final ProjectAccessService accessService;
    private final AuditService auditService;
    private final CommentService commentService;
    private final TestCaseMapper testCaseMapper;
    private final TestCaseVersionRepository versionRepository;

    /**
     * The status a plain write (create, update, bulk, MCP) may set. With review on, ACTIVE is
     * refused (it is reached through {@link #approve}) and deprecating needs ADMIN.
     *
     * @param from null on create
     */
    public void checkStatusWrite(Project project, TestCaseStatus from, TestCaseStatus to, UUID userId) {
        if (!project.isReviewRequired() || to == null || to == from) {
            return;
        }
        if (to == TestCaseStatus.ACTIVE) {
            throw new ReviewRequiredException();
        }
        if (to == TestCaseStatus.DEPRECATED && !roleOf(project, userId).satisfies(ProjectRole.ADMIN)) {
            throw new ForbiddenException("Only a project admin can deprecate a case while review is required");
        }
    }

    /**
     * Called by {@code TestCaseService.update} after the edit. A content edit to an approved case
     * sends it back to review; a cosmetic one (labels, priority, folder) carries the approval over
     * to the new version, since the wording it approved is unchanged.
     */
    void afterEdit(TestCase testCase, TestCaseStatus statusBefore, int versionBefore, boolean contentChanged) {
        boolean approvalWasCurrent = Objects.equals(testCase.getApprovedVersion(), versionBefore);
        if (!contentChanged) {
            if (approvalWasCurrent) {
                testCase.setApprovedVersion(testCase.getCurrentVersion());
            }
            return;
        }
        if (testCase.getProject().isReviewRequired() && statusBefore == TestCaseStatus.ACTIVE
                && testCase.getStatus() == TestCaseStatus.ACTIVE) {
            testCase.setStatus(TestCaseStatus.IN_REVIEW);
        }
    }

    /**
     * Results executed against wording that was never approved (PRD-033 §3.4), for the run report.
     * A version counts as approved if it is the live approval or a snapshot records it as such, so
     * an old result on a then-approved version isn't flagged just because a newer one exists.
     * ACTIVE cases approved before review was switched on (no approval recorded) aren't flagged.
     */
    public Set<UUID> resultsOnUnapprovedWording(Project project, List<TestResult> results) {
        if (!project.isReviewRequired()) {
            return Set.of();
        }
        List<UUID> caseIds = results.stream()
                .filter(r -> r.getTestCase() != null && r.getExecutedVersion() != null)
                .map(r -> r.getTestCase().getId())
                .distinct()
                .toList();
        if (caseIds.isEmpty()) {
            return Set.of();
        }
        Set<String> approvedSnapshots = versionRepository.findApprovedVersionKeys(caseIds);
        Set<UUID> flagged = new HashSet<>();
        for (TestResult result : results) {
            TestCase testCase = result.getTestCase();
            Integer executed = result.getExecutedVersion();
            if (testCase == null || executed == null) {
                continue;
            }
            boolean legacyApproved = testCase.getApprovedVersion() == null
                    && testCase.getStatus() == TestCaseStatus.ACTIVE;
            boolean approved = executed.equals(testCase.getApprovedVersion())
                    || approvedSnapshots.contains(testCase.getId() + ":" + executed);
            if (!approved && !legacyApproved) {
                flagged.add(result.getId());
            }
        }
        return flagged;
    }

    /** True when the update changes title, description, preconditions or steps. */
    static boolean isContentEdit(TestCase testCase, UpdateTestCaseRequest request) {
        return differs(request.title(), testCase.getTitle())
                || differs(request.description(), testCase.getDescription())
                || differs(request.preconditions(), testCase.getPreconditions())
                || (request.steps() != null && !sameSteps(request.steps(), testCase.getSteps()));
    }

    @Transactional
    public TestCaseResponse submitForReview(UUID projectId, UUID id, UUID userId) {
        TestCase testCase = require(projectId, id);
        if (testCase.getStatus() != TestCaseStatus.DRAFT) {
            throw new IllegalArgumentException("Only a DRAFT case can be submitted for review");
        }
        testCase.setStatus(TestCaseStatus.IN_REVIEW);
        auditService.log(projectId, userId, AuditAction.STATUS_CHANGED, AuditEntityType.TEST_CASE,
                id, testCase.getTitle(), "submitted for review");
        return testCaseMapper.toResponse(testCaseRepository.save(testCase));
    }

    @Transactional
    public TestCaseResponse approve(UUID projectId, UUID id, int version, boolean force, UUID userId) {
        TestCase testCase = require(projectId, id);
        requireInReview(testCase);
        if (testCase.getCurrentVersion() != version) {
            throw new ConflictException("The case changed since you loaded it (now v"
                    + testCase.getCurrentVersion() + "); review the latest version");
        }
        boolean forced = requireReviewer(testCase, userId, force);
        testCase.setStatus(TestCaseStatus.ACTIVE);
        testCase.setApprovedBy(userId);
        testCase.setApprovedAt(Instant.now());
        testCase.setApprovedVersion(version);
        auditService.log(projectId, userId, AuditAction.UPDATED, AuditEntityType.TEST_CASE, id,
                testCase.getTitle(), "approved v" + version + (forced ? " (forced by a system admin)" : ""));
        return testCaseMapper.toResponse(testCaseRepository.save(testCase));
    }

    @Transactional
    public TestCaseResponse requestChanges(UUID projectId, UUID id, String comment, UUID userId) {
        TestCase testCase = require(projectId, id);
        requireInReview(testCase);
        requireReviewer(testCase, userId, false);
        testCase.setStatus(TestCaseStatus.DRAFT);
        if (comment != null && !comment.isBlank()) {
            commentService.create(projectId, CommentEntityType.TEST_CASE, id, new CreateCommentRequest(comment), userId);
        }
        auditService.log(projectId, userId, AuditAction.STATUS_CHANGED, AuditEntityType.TEST_CASE, id,
                testCase.getTitle(), "changes requested");
        return testCaseMapper.toResponse(testCaseRepository.save(testCase));
    }

    public ReviewCapabilitiesResponse capabilities(UUID projectId, UUID id, UUID userId) {
        TestCase testCase = require(projectId, id);
        boolean reviewRequired = testCase.getProject().isReviewRequired();
        ProjectRole role = roleOf(testCase.getProject(), userId);
        boolean canSubmit = testCase.getStatus() == TestCaseStatus.DRAFT && role.satisfies(ProjectRole.TESTER);
        String reason = testCase.getStatus() == TestCaseStatus.IN_REVIEW ? reviewerRefusal(testCase, userId) : null;
        boolean canApprove = testCase.getStatus() == TestCaseStatus.IN_REVIEW && reason == null;
        return new ReviewCapabilitiesResponse(reviewRequired, canSubmit, canApprove, reason);
    }

    /**
     * Whether {@code userId} may approve or request changes on this case, and if not why. The
     * approver must hold the project's reviewer role and, with review on, be neither the author
     * nor the last editor: approving your own wording is what the review exists to prevent.
     */
    public String reviewerRefusal(TestCase testCase, UUID userId) {
        Project project = testCase.getProject();
        ProjectRole role = roleOf(project, userId);
        ProjectRole required = project.isReviewRequired() ? project.getReviewerMinRole() : ProjectRole.TESTER;
        if (!role.satisfies(required)) {
            return "Reviewing needs the " + required + " role";
        }
        if (project.isReviewRequired()
                && (userId.equals(testCase.getCreatedBy()) || userId.equals(testCase.getUpdatedBy()))) {
            return "You wrote or last edited this case; another member has to review it";
        }
        return null;
    }

    /** @return true when the approval only passed because a system admin forced it. */
    private boolean requireReviewer(TestCase testCase, UUID userId, boolean force) {
        String refusal = reviewerRefusal(testCase, userId);
        if (refusal == null) {
            return false;
        }
        boolean authorRuleOnly = roleOf(testCase.getProject(), userId).satisfies(
                testCase.getProject().getReviewerMinRole());
        if (force && authorRuleOnly && accessService.isSystemAdmin(userId)) {
            return true;
        }
        throw new ForbiddenException(refusal);
    }

    private static void requireInReview(TestCase testCase) {
        if (testCase.getStatus() != TestCaseStatus.IN_REVIEW) {
            throw new IllegalArgumentException("Only a case IN_REVIEW can be approved or sent back");
        }
    }

    /** VIEWER for an unknown caller, so a missing identity can never pass a role check. */
    private ProjectRole roleOf(Project project, UUID userId) {
        if (userId == null) {
            return ProjectRole.VIEWER;
        }
        try {
            return accessService.requireMember(userId, project.getId());
        } catch (ForbiddenException e) {
            return ProjectRole.VIEWER;
        }
    }

    private TestCase require(UUID projectId, UUID id) {
        return testCaseRepository.findById(id)
                .filter(tc -> tc.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("TestCase", id));
    }

    /** Null means "not sent"; "" and null are the same empty text. */
    private static boolean differs(String requested, String current) {
        return requested != null && !Objects.equals(blankToNull(requested), blankToNull(current));
    }

    private static boolean sameSteps(List<TestStepRequest> requested, List<TestStep> current) {
        if (requested.size() != current.size()) {
            return false;
        }
        List<TestStep> ordered = current.stream().sorted(Comparator.comparingInt(TestStep::getOrderIndex)).toList();
        for (int i = 0; i < requested.size(); i++) {
            TestStepRequest want = requested.get(i);
            TestStep have = ordered.get(i);
            if (!Objects.equals(blankToNull(want.action()), blankToNull(have.getAction()))
                    || !Objects.equals(blankToNull(want.expectedResult()), blankToNull(have.getExpectedResult()))
                    || !Objects.equals(blankToNull(want.testData()), blankToNull(have.getTestData()))) {
                return false;
            }
        }
        return true;
    }

    private static String blankToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    /**
     * A plain status write refused because the project requires review. Still a 400 (it extends
     * IllegalArgumentException); its own type lets callers that map other argument errors, like
     * the bulk MCP tool, pass this message through unchanged.
     */
    public static class ReviewRequiredException extends IllegalArgumentException {
        ReviewRequiredException() {
            super(USE_APPROVE);
        }
    }
}
