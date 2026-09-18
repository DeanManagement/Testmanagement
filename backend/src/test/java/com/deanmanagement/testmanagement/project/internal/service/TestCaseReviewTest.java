package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.myqueue.MyQueueResponse;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.CreateTestRunRequest;
import com.deanmanagement.testmanagement.project.internal.ci.CiResult;
import com.deanmanagement.testmanagement.project.internal.dto.TestStepRequest;
import com.deanmanagement.testmanagement.project.internal.dto.UpdateTestCaseRequest;
import com.deanmanagement.testmanagement.project.internal.dto.io.ImportResultResponse;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.BulkStatusRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.CreateTestCaseRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.TestCaseResponse;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectMember;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.project.internal.repository.AuditEntryRepository;
import com.deanmanagement.testmanagement.project.internal.repository.CommentRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectMemberRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.shared.exception.ConflictException;
import com.deanmanagement.testmanagement.shared.exception.ForbiddenException;
import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.internal.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PRD-033 review rules. Each actor is authenticated with a String principal, the way the JWT
 * filter does it, so JPA auditing records the real author and last editor that the
 * not-the-author rule reads.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class TestCaseReviewTest {

    @Autowired
    private TestCaseService testCaseService;
    @Autowired
    private TestCaseReviewService reviewService;
    @Autowired
    private TestCaseImportExportService importService;
    @Autowired
    private CiIngestionService ciIngestionService;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private ProjectMemberRepository memberRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TestCaseRepository testCaseRepository;
    @Autowired
    private AuditEntryRepository auditEntryRepository;
    @Autowired
    private CommentRepository commentRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private TestRunService testRunService;
    @Autowired
    private MyQueueService myQueueService;

    private Project project;
    private UUID author;
    private UUID admin;
    private UUID otherAdmin;
    private UUID tester;
    private UUID systemAdmin;

    @BeforeEach
    void setUp() {
        project = new Project();
        project.setName("Reviewed");
        project.setKey("REV");
        project.setReviewRequired(true);
        project = projectRepository.save(project);
        author = member(ProjectRole.TESTER);
        admin = member(ProjectRole.ADMIN);
        otherAdmin = member(ProjectRole.ADMIN);
        tester = member(ProjectRole.TESTER);
        systemAdmin = user(true);
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private UUID user(boolean systemAdminFlag) {
        User user = new User();
        user.setEmail("u-" + UUID.randomUUID() + "@test.local");
        user.setDisplayName("u");
        user.setPasswordHash("x");
        user.setSystemAdmin(systemAdminFlag);
        return userRepository.save(user).getId();
    }

    private UUID member(ProjectRole role) {
        UUID id = user(false);
        ProjectMember member = new ProjectMember();
        member.setUser(userRepository.getReferenceById(id));
        member.setProject(project);
        member.setRole(role);
        memberRepository.save(member);
        return id;
    }

    /**
     * Switches the acting user. Pending changes are flushed first: auditing stamps updatedBy at
     * flush time, and in one test transaction a later actor would otherwise be credited with an
     * earlier actor's edit. Production has one transaction per request, so it can't happen there.
     */
    private UUID as(UUID userId) {
        entityManager.flush();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of()));
        return userId;
    }

    private TestCaseResponse draftBy(UUID userId) {
        as(userId);
        return testCaseService.create(project.getId(), new CreateTestCaseRequest("Checkout", "pays", null,
                Priority.MEDIUM, TestCaseStatus.DRAFT, Set.of(), List.of(new TestStepRequest("Pay", "Paid", null)),
                null), userId);
    }

    private TestCaseResponse inReviewBy(UUID userId) {
        TestCaseResponse draft = draftBy(userId);
        return reviewService.submitForReview(project.getId(), draft.id(), userId);
    }

    private TestCaseResponse approvedCase() {
        TestCaseResponse inReview = inReviewBy(author);
        return reviewService.approve(project.getId(), inReview.id(), inReview.currentVersion(), false, as(admin));
    }

    private UpdateTestCaseRequest statusOnly(TestCaseStatus status) {
        return new UpdateTestCaseRequest(null, null, null, null, status, null, null);
    }

    private void reviewOff() {
        project.setReviewRequired(false);
        projectRepository.save(project);
    }

    @Nested
    class PlainStatusWrites {

        @Test
        void createAsActiveIsRefused() {
            as(author);
            assertThatThrownBy(() -> testCaseService.create(project.getId(), new CreateTestCaseRequest("X", null, null,
                    Priority.LOW, TestCaseStatus.ACTIVE, Set.of(), List.of(), null), author))
                    .isInstanceOf(TestCaseReviewService.ReviewRequiredException.class)
                    .hasMessageContaining("IN_REVIEW");
        }

        @Test
        void updateToActiveIsRefused() {
            UUID id = inReviewBy(author).id();

            assertThatThrownBy(() -> testCaseService.update(project.getId(), id, statusOnly(TestCaseStatus.ACTIVE), as(admin)))
                    .isInstanceOf(TestCaseReviewService.ReviewRequiredException.class);
        }

        @Test
        void bulkToActiveChangesNothing() {
            UUID first = draftBy(author).id();
            UUID second = draftBy(author).id();

            assertThatThrownBy(() -> testCaseService.bulkUpdateStatus(project.getId(),
                    new BulkStatusRequest(Set.of(first, second), TestCaseStatus.ACTIVE), as(admin)))
                    .isInstanceOf(TestCaseReviewService.ReviewRequiredException.class);
            assertThat(testCaseRepository.findById(first).orElseThrow().getStatus()).isEqualTo(TestCaseStatus.DRAFT);
        }

        @Test
        void deprecatingNeedsAnAdmin() {
            UUID id = approvedCase().id();

            assertThatThrownBy(() -> testCaseService.update(project.getId(), id,
                    statusOnly(TestCaseStatus.DEPRECATED), as(tester)))
                    .isInstanceOf(ForbiddenException.class);
            assertThat(testCaseService.update(project.getId(), id, statusOnly(TestCaseStatus.DEPRECATED), as(admin))
                    .status()).isEqualTo(TestCaseStatus.DEPRECATED);
        }

        @Test
        void withoutReviewEverythingWorksAsBefore() {
            reviewOff();
            UUID id = draftBy(author).id();

            assertThat(testCaseService.update(project.getId(), id, statusOnly(TestCaseStatus.ACTIVE), as(author))
                    .status()).isEqualTo(TestCaseStatus.ACTIVE);
            assertThat(testCaseService.update(project.getId(), id, statusOnly(TestCaseStatus.DEPRECATED), as(tester))
                    .status()).isEqualTo(TestCaseStatus.DEPRECATED);
        }
    }

    @Nested
    class Approve {

        @Test
        void recordsWhoApprovedWhichVersion() {
            TestCaseResponse inReview = inReviewBy(author);

            TestCaseResponse approved = reviewService.approve(project.getId(), inReview.id(),
                    inReview.currentVersion(), false, as(admin));

            assertThat(approved.status()).isEqualTo(TestCaseStatus.ACTIVE);
            assertThat(approved.approvedBy()).isEqualTo(admin);
            assertThat(approved.approvedVersion()).isEqualTo(inReview.currentVersion());
            assertThat(approved.approvedAt()).isNotNull();
            assertThat(auditEntryRepository.findAll()).anyMatch(entry -> entry.getEntityType() == AuditEntityType.TEST_CASE
                    && ("approved v" + inReview.currentVersion()).equals(entry.getDetails()));
        }

        @Test
        void theAuthorCannotApproveEvenAsAdmin() {
            TestCaseResponse inReview = inReviewBy(admin);

            assertThatThrownBy(() -> reviewService.approve(project.getId(), inReview.id(),
                    inReview.currentVersion(), false, as(admin)))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        void theLastEditorCannotApprove() {
            TestCaseResponse draft = draftBy(author);
            as(otherAdmin);
            testCaseService.update(project.getId(), draft.id(), new UpdateTestCaseRequest("Checkout v2", null, null,
                    null, null, null, null), otherAdmin);
            TestCaseResponse inReview = reviewService.submitForReview(project.getId(), draft.id(), as(author));
            as(otherAdmin);
            // otherAdmin edited the wording; author submitted, so the last editor is the author now.
            assertThat(reviewService.approve(project.getId(), inReview.id(), inReview.currentVersion(), false,
                    otherAdmin).status()).isEqualTo(TestCaseStatus.ACTIVE);
        }

        @Test
        void aTesterCannotApproveWhenReviewersMustBeAdmins() {
            TestCaseResponse inReview = inReviewBy(author);

            assertThatThrownBy(() -> reviewService.approve(project.getId(), inReview.id(),
                    inReview.currentVersion(), false, as(tester)))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("ADMIN");
        }

        @Test
        void aTesterCanApproveWhenTestersReview() {
            project.setReviewerMinRole(ProjectRole.TESTER);
            projectRepository.save(project);
            TestCaseResponse inReview = inReviewBy(author);

            assertThat(reviewService.approve(project.getId(), inReview.id(), inReview.currentVersion(), false,
                    as(tester)).status()).isEqualTo(TestCaseStatus.ACTIVE);
        }

        @Test
        void aStaleVersionIsAConflict() {
            TestCaseResponse inReview = inReviewBy(author);

            assertThatThrownBy(() -> reviewService.approve(project.getId(), inReview.id(),
                    inReview.currentVersion() - 1, false, as(admin)))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        void onlyACaseInReviewCanBeApproved() {
            TestCaseResponse draft = draftBy(author);

            assertThatThrownBy(() -> reviewService.approve(project.getId(), draft.id(), draft.currentVersion(), false,
                    as(admin)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void aSystemAdminCanForceOverTheAuthorRuleAndItIsAudited() {
            TestCaseResponse inReview = inReviewBy(systemAdmin);

            TestCaseResponse approved = reviewService.approve(project.getId(), inReview.id(),
                    inReview.currentVersion(), true, as(systemAdmin));

            assertThat(approved.status()).isEqualTo(TestCaseStatus.ACTIVE);
            assertThat(auditEntryRepository.findAll())
                    .anyMatch(entry -> entry.getDetails() != null && entry.getDetails().contains("forced"));
        }

        @Test
        void forceDoesNothingForAnOrdinaryMember() {
            TestCaseResponse inReview = inReviewBy(admin);

            assertThatThrownBy(() -> reviewService.approve(project.getId(), inReview.id(),
                    inReview.currentVersion(), true, as(admin)))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        void requestingChangesSendsItBackWithAComment() {
            TestCaseResponse inReview = inReviewBy(author);

            TestCaseResponse back = reviewService.requestChanges(project.getId(), inReview.id(),
                    "Step 2 is ambiguous", as(admin));

            assertThat(back.status()).isEqualTo(TestCaseStatus.DRAFT);
            assertThat(commentRepository.findAll()).anyMatch(c -> "Step 2 is ambiguous".equals(c.getContent()));
        }

        @Test
        void capabilitiesTellTheAuthorWhyTheyCannotApprove() {
            TestCaseResponse inReview = inReviewBy(admin);

            var forAuthor = reviewService.capabilities(project.getId(), inReview.id(), as(admin));
            var forAdmin = reviewService.capabilities(project.getId(), inReview.id(), as(otherAdmin));

            assertThat(forAuthor.canApprove()).isFalse();
            assertThat(forAuthor.reason()).contains("another member");
            assertThat(forAdmin.canApprove()).isTrue();
        }
    }

    @Nested
    class EditsToApprovedCases {

        @Test
        void aContentEditReturnsItToReviewAndKeepsTheApprovedVersion() {
            TestCaseResponse approved = approvedCase();

            TestCaseResponse edited = testCaseService.update(project.getId(), approved.id(),
                    new UpdateTestCaseRequest("Checkout, reworded", null, null, null, TestCaseStatus.ACTIVE, null, null),
                    as(tester));

            assertThat(edited.status()).isEqualTo(TestCaseStatus.IN_REVIEW);
            assertThat(edited.approvedVersion()).isEqualTo(approved.approvedVersion());
            assertThat(edited.currentVersion()).isGreaterThan(approved.approvedVersion());
        }

        @Test
        void aStepEditCountsAsContent() {
            TestCaseResponse approved = approvedCase();

            TestCaseResponse edited = testCaseService.update(project.getId(), approved.id(),
                    new UpdateTestCaseRequest(null, null, null, null, null, null,
                            List.of(new TestStepRequest("Pay by card", "Paid", null))), as(tester));

            assertThat(edited.status()).isEqualTo(TestCaseStatus.IN_REVIEW);
        }

        @Test
        void aCosmeticEditStaysApprovedAndCarriesTheApprovalForward() {
            TestCaseResponse approved = approvedCase();

            // The UI sends the whole case back; only labels and priority differ.
            TestCaseResponse edited = testCaseService.update(project.getId(), approved.id(),
                    new UpdateTestCaseRequest(approved.title(), approved.description(), "", Priority.HIGH,
                            TestCaseStatus.ACTIVE, new HashSet<>(Set.of("smoke")), List.of(new TestStepRequest("Pay", "Paid", ""))),
                    as(tester));

            assertThat(edited.status()).isEqualTo(TestCaseStatus.ACTIVE);
            assertThat(edited.approvedVersion()).isEqualTo(edited.currentVersion());
        }
    }

    @Nested
    class OtherWriters {

        @Test
        void ciIngestionCreatesNewCasesInReview() {
            as(author);
            ciIngestionService.ingest(project.getKey(), "Nightly", null, null,
                    List.of(new CiResult("suite", "new automated test", TestResultStatus.PASSED, null, List.of(), null)), null);

            TestCase created = testCaseRepository.findFirstByProjectIdAndTitle(project.getId(), "new automated test")
                    .orElseThrow();
            assertThat(created.getStatus()).isEqualTo(TestCaseStatus.IN_REVIEW);
        }

        @Test
        void importTurnsActiveRowsIntoInReviewWithAWarning() {
            String csv = "title,status\nImported,ACTIVE\n";

            ImportResultResponse result = importService.importData(project.getId(), "cases.csv",
                    csv.getBytes(StandardCharsets.UTF_8), false, as(author));

            assertThat(result.imported()).isEqualTo(1);
            assertThat(result.warnings()).singleElement().satisfies(w -> assertThat(w.message()).contains("IN_REVIEW"));
            assertThat(testCaseRepository.findFirstByProjectIdAndTitle(project.getId(), "Imported").orElseThrow()
                    .getStatus()).isEqualTo(TestCaseStatus.IN_REVIEW);
        }

        @Test
        void importKeepsActiveWithoutReview() {
            reviewOff();

            ImportResultResponse result = importService.importData(project.getId(), "cases.csv",
                    "title,status\nImported,ACTIVE\n".getBytes(StandardCharsets.UTF_8), false, as(author));

            assertThat(result.warnings()).isEmpty();
            assertThat(testCaseRepository.findFirstByProjectIdAndTitle(project.getId(), "Imported").orElseThrow()
                    .getStatus()).isEqualTo(TestCaseStatus.ACTIVE);
        }
    }

    @Nested
    class RunReports {

        private UUID runWith(UUID testCaseId) {
            return testRunService.create(project.getId(), new CreateTestRunRequest("Run", null, Set.of(testCaseId),
                    null, null), author).id();
        }

        private Set<UUID> flaggedIn(UUID runId) {
            return testRunService.getReport(project.getId(), runId).unapprovedResultIds();
        }

        @Test
        void approvedWordingIsNotFlagged() {
            UUID runId = runWith(approvedCase().id());

            assertThat(flaggedIn(runId)).isEmpty();
        }

        @Test
        void wordingInReviewIsFlaggedButEarlierApprovedRunsStayClean() {
            TestCaseResponse approved = approvedCase();
            UUID approvedRun = runWith(approved.id());
            testCaseService.update(project.getId(), approved.id(), new UpdateTestCaseRequest("Reworded", null, null,
                    null, null, null, null), as(tester));

            UUID laterRun = runWith(approved.id());

            assertThat(flaggedIn(laterRun)).hasSize(1);
            assertThat(flaggedIn(approvedRun)).as("v1 was approved when it ran").isEmpty();
        }

        @Test
        void casesApprovedBeforeReviewWasSwitchedOnAreNotFlagged() {
            reviewOff();
            as(author);
            TestCaseResponse legacy = testCaseService.create(project.getId(), new CreateTestCaseRequest("Legacy",
                    null, null, Priority.LOW, TestCaseStatus.ACTIVE, Set.of(), List.of(), null), author);
            project.setReviewRequired(true);
            projectRepository.save(project);

            assertThat(flaggedIn(runWith(legacy.id()))).isEmpty();
        }

        @Test
        void nothingIsFlaggedWithoutReview() {
            UUID runId = runWith(draftBy(author).id());
            reviewOff();

            assertThat(flaggedIn(runId)).isEmpty();
        }
    }

    @Nested
    class Queue {

        private List<UUID> queuedFor(UUID userId) {
            return myQueueService.buildFor(userId).awaitingReview().stream()
                    .map(MyQueueResponse.ReviewTestCaseItem::id).toList();
        }

        @Test
        void listsCasesAwaitingAnEligibleReviewer() {
            UUID id = inReviewBy(author).id();
            as(admin);

            assertThat(queuedFor(admin)).containsExactly(id);
            assertThat(queuedFor(author)).as("not your own").isEmpty();
            assertThat(queuedFor(tester)).as("below the reviewer role").isEmpty();
        }

        @Test
        void includesTestersWhenTheyMayReview() {
            project.setReviewerMinRole(ProjectRole.TESTER);
            projectRepository.save(project);
            UUID id = inReviewBy(author).id();
            as(admin);

            assertThat(queuedFor(tester)).containsExactly(id);
        }
    }
}
