package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.bugReport.BugReportResponse;
import com.deanmanagement.testmanagement.project.internal.dto.bugReport.CreateBugReportRequest;
import com.deanmanagement.testmanagement.project.internal.dto.session.CreateExploratorySessionRequest;
import com.deanmanagement.testmanagement.project.internal.dto.session.CreateSessionNoteRequest;
import com.deanmanagement.testmanagement.project.internal.dto.session.ExploratorySessionResponse;
import com.deanmanagement.testmanagement.project.internal.dto.session.SessionNoteResponse;
import com.deanmanagement.testmanagement.project.internal.dto.session.UpdateSessionNoteRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testplan.TestPlanSummaryResponse;
import com.deanmanagement.testmanagement.project.internal.entity.ExploratorySession;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectMember;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.SessionNoteType;
import com.deanmanagement.testmanagement.project.internal.entity.TestPlan;
import com.deanmanagement.testmanagement.project.internal.entity.TestPlanStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import com.deanmanagement.testmanagement.project.internal.repository.BugReportRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ExploratorySessionRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectMemberRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestPlanRepository;
import com.deanmanagement.testmanagement.shared.exception.ConflictException;
import com.deanmanagement.testmanagement.shared.exception.ForbiddenException;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** PRD-034 session rules, run as real users so note authorship comes from JPA auditing. */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class ExploratorySessionServiceTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};

    @Autowired private ExploratorySessionService sessionService;
    @Autowired private ExploratorySessionRepository sessionRepository;
    @Autowired private BugReportService bugReportService;
    @Autowired private BugReportRepository bugReportRepository;
    @Autowired private TestPlanService testPlanService;
    @Autowired private ProjectEnvironmentService environmentService;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectMemberRepository memberRepository;
    @Autowired private TestPlanRepository testPlanRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager entityManager;

    private Project project;
    private Project otherProject;
    private UUID tester;
    private UUID otherTester;
    private UUID admin;

    @BeforeEach
    void setUp() {
        project = project("EXP");
        otherProject = project("OXP");
        tester = member(project, ProjectRole.TESTER);
        otherTester = member(project, ProjectRole.TESTER);
        admin = member(project, ProjectRole.ADMIN);
        as(tester);
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private Project project(String key) {
        Project p = new Project();
        p.setName("Explore " + key);
        p.setKey(key);
        p.setBugReportsEnabled(true);
        return projectRepository.save(p);
    }

    private UUID member(Project target, ProjectRole role) {
        User u = new User();
        u.setEmail("u-" + UUID.randomUUID() + "@test.local");
        u.setDisplayName("Tester " + role);
        u.setPasswordHash("x");
        u = userRepository.save(u);
        ProjectMember m = new ProjectMember();
        m.setUser(u);
        m.setProject(target);
        m.setRole(role);
        memberRepository.save(m);
        return u.getId();
    }

    /** Flushes first, so auditing credits pending changes to the previous user. */
    private UUID as(UUID userId) {
        entityManager.flush();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of()));
        return userId;
    }

    private ExploratorySessionResponse newSession() {
        return sessionService.create(project.getId(),
                new CreateExploratorySessionRequest("Attack checkout with odd currencies", 60, null, null, null, tester),
                tester);
    }

    private ExploratorySessionResponse running() {
        return sessionService.start(project.getId(), newSession().id(), tester);
    }

    private SessionNoteResponse note(UUID sessionId, SessionNoteType type, String body) {
        return sessionService.addNote(project.getId(), sessionId, new CreateSessionNoteRequest(type, body, null));
    }

    @Nested
    class Lifecycle {

        @Test
        void keysAreNumberedPerProject() {
            assertThat(newSession().key()).isEqualTo("EXP-Session-1");
            assertThat(newSession().key()).isEqualTo("EXP-Session-2");
        }

        @Test
        void startThenCompleteRecordsTimesAndTheDebrief() {
            ExploratorySessionResponse started = running();
            assertThat(started.status()).isEqualTo(TestRunStatus.IN_PROGRESS);
            assertThat(started.startedAt()).isNotNull();

            ExploratorySessionResponse done = sessionService.complete(project.getId(), started.id(),
                    "Rounding bug in JPY", tester);

            assertThat(done.status()).isEqualTo(TestRunStatus.COMPLETED);
            assertThat(done.endedAt()).isNotNull();
            assertThat(done.summary()).isEqualTo("Rounding bug in JPY");
        }

        @Test
        void invalidTransitionsAreRefused() {
            ExploratorySessionResponse planned = newSession();
            assertThatThrownBy(() -> sessionService.complete(project.getId(), planned.id(), null, tester))
                    .isInstanceOf(IllegalArgumentException.class);

            sessionService.start(project.getId(), planned.id(), tester);
            assertThatThrownBy(() -> sessionService.start(project.getId(), planned.id(), tester))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void abortIsAnEndStateToo() {
            assertThat(sessionService.abort(project.getId(), running().id(), "blocked by outage", tester).status())
                    .isEqualTo(TestRunStatus.ABORTED);
        }

        @Test
        void anotherProjectsPlanOrANonMemberTesterIsNotFound() {
            TestPlan foreignPlan = new TestPlan();
            foreignPlan.setProject(otherProject);
            foreignPlan.setName("Theirs");
            foreignPlan.setStatus(TestPlanStatus.OPEN);
            UUID foreignPlanId = testPlanRepository.save(foreignPlan).getId();
            UUID outsider = member(otherProject, ProjectRole.TESTER);

            assertThatThrownBy(() -> sessionService.create(project.getId(), new CreateExploratorySessionRequest(
                    "x", 30, foreignPlanId, null, null, null), tester))
                    .isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> sessionService.create(project.getId(), new CreateExploratorySessionRequest(
                    "x", 30, null, null, null, outsider), tester))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void aSessionInAnotherProjectIsNotFound() {
            UUID id = newSession().id();

            assertThatThrownBy(() -> sessionService.get(otherProject.getId(), id))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void environmentNamesResolveAgainstTheCatalogue() {
            environmentService.resolve(project.getId(), null, "Staging");

            ExploratorySessionResponse session = sessionService.create(project.getId(),
                    new CreateExploratorySessionRequest("x", 30, null, " staging ", null, null), tester);

            assertThat(session.environment()).isEqualTo("Staging");
        }
    }

    @Nested
    class Notes {

        @Test
        void aPlannedSessionTakesNoNotes() {
            UUID id = newSession().id();

            assertThatThrownBy(() -> note(id, SessionNoteType.NOTE, "too early"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void notesAreLoggedNewestFirst() {
            UUID id = running().id();
            note(id, SessionNoteType.NOTE, "first");
            note(id, SessionNoteType.BUG, "second");

            assertThat(sessionService.get(project.getId(), id).notes())
                    .extracting(SessionNoteResponse::body).containsExactly("second", "first");
        }

        @Test
        void aNoteCannotPredateTheStartOrLieInTheFuture() {
            ExploratorySessionResponse session = running();

            assertThatThrownBy(() -> sessionService.addNote(project.getId(), session.id(), new CreateSessionNoteRequest(
                    SessionNoteType.NOTE, "x", session.startedAt().minusSeconds(60))))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> sessionService.addNote(project.getId(), session.id(), new CreateSessionNoteRequest(
                    SessionNoteType.NOTE, "x", Instant.now().plus(Duration.ofHours(1)))))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void lateNotesAreAcceptedForADayAfterCompletion() {
            UUID id = running().id();
            sessionService.complete(project.getId(), id, null, tester);

            assertThat(note(id, SessionNoteType.IDEA, "debrief thought").type()).isEqualTo(SessionNoteType.IDEA);

            ExploratorySession stored = sessionRepository.findById(id).orElseThrow();
            stored.setEndedAt(Instant.now().minus(Duration.ofHours(25)));
            sessionRepository.saveAndFlush(stored);
            assertThatThrownBy(() -> note(id, SessionNoteType.NOTE, "too late"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void onlyTheAuthorOrAnAdminChangesANote() {
            UUID id = running().id();
            UUID noteId = note(id, SessionNoteType.NOTE, "mine").id();

            as(otherTester);
            assertThatThrownBy(() -> sessionService.updateNote(project.getId(), id, noteId,
                    new UpdateSessionNoteRequest(null, "theirs now"), otherTester))
                    .isInstanceOf(ForbiddenException.class);

            as(admin);
            assertThat(sessionService.updateNote(project.getId(), id, noteId,
                    new UpdateSessionNoteRequest(SessionNoteType.QUESTION, null), admin).type())
                    .isEqualTo(SessionNoteType.QUESTION);
        }

        @Test
        void aNoteHoldsOneAllowlistedImage() {
            UUID id = running().id();
            UUID noteId = note(id, SessionNoteType.BUG, "see screenshot").id();

            sessionService.uploadImage(project.getId(), id, noteId, "shot.png", "image/png", PNG, tester);

            assertThat(sessionService.get(project.getId(), id).notes().get(0).hasImage()).isTrue();
            assertThat(sessionService.getImage(project.getId(), id, noteId).getData()).isEqualTo(PNG);
            assertThatThrownBy(() -> sessionService.uploadImage(project.getId(), id, noteId, "x.svg",
                    "image/svg+xml", new byte[]{1}, tester))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void anImageThroughAnotherProjectsPathIsNotFound() {
            UUID id = running().id();
            UUID noteId = note(id, SessionNoteType.BUG, "x").id();
            sessionService.uploadImage(project.getId(), id, noteId, "shot.png", "image/png", PNG, tester);

            assertThatThrownBy(() -> sessionService.getImage(otherProject.getId(), id, noteId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class Links {

        @Test
        void aBugFiledFromASessionLinksBackAndTakesItsEnvironment() {
            UUID id = sessionService.start(project.getId(), sessionService.create(project.getId(),
                    new CreateExploratorySessionRequest("x", 30, null, "Staging", null, null), tester).id(), tester).id();

            BugReportResponse bug = bugReportService.create(project.getId(), new CreateBugReportRequest("JPY rounds wrong",
                    null, null, null, null, Priority.HIGH, null, null, null, null, null, id), tester);

            assertThat(bug.exploratorySessionId()).isEqualTo(id);
            assertThat(bug.environment()).isEqualTo("Staging");
            assertThat(sessionService.get(project.getId(), id).bugs())
                    .extracting(ExploratorySessionResponse.LinkedBug::title).containsExactly("JPY rounds wrong");
        }

        @Test
        void anotherProjectsSessionCannotBeLinked() {
            as(member(otherProject, ProjectRole.TESTER));
            UUID foreignSession = sessionService.create(otherProject.getId(),
                    new CreateExploratorySessionRequest("x", 30, null, null, null, null), null).id();

            assertThatThrownBy(() -> bugReportService.create(project.getId(), new CreateBugReportRequest("x", null,
                    null, null, null, Priority.LOW, null, null, null, null, null, foreignSession), tester))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void deletingASessionKeepsItsBugs() {
            UUID id = running().id();
            UUID bugId = bugReportService.create(project.getId(), new CreateBugReportRequest("stays", null, null, null,
                    null, Priority.LOW, null, null, null, null, null, id), tester).id();

            sessionService.delete(project.getId(), id, tester);
            entityManager.flush();
            entityManager.clear();

            assertThat(bugReportRepository.findById(bugId)).isPresent();
        }

        @Test
        void planSummaryReportsSessionsApartFromRuns() {
            TestPlan plan = new TestPlan();
            plan.setProject(project);
            plan.setName("2.4");
            plan.setStatus(TestPlanStatus.OPEN);
            UUID planId = testPlanRepository.save(plan).getId();
            UUID id = sessionService.create(project.getId(),
                    new CreateExploratorySessionRequest("x", 30, planId, null, null, null), tester).id();
            sessionService.start(project.getId(), id, tester);
            sessionService.complete(project.getId(), id, null, tester);

            TestPlanSummaryResponse summary = testPlanService.getSummary(project.getId(), planId);

            assertThat(summary.sessions().total()).isEqualTo(1);
            assertThat(summary.sessions().completed()).isEqualTo(1);
            assertThat(summary.totalRuns()).isZero();
            assertThat(summary.passRate()).isZero();
        }

        @Test
        void environmentsInUseBySessionsCanBeMergedButNotDeleted() {
            UUID stg = environmentService.resolve(project.getId(), null, "stg").getId();
            UUID staging = environmentService.resolve(project.getId(), null, "staging").getId();
            UUID id = sessionService.create(project.getId(),
                    new CreateExploratorySessionRequest("x", 30, null, null, stg, null), tester).id();

            assertThatThrownBy(() -> environmentService.delete(project.getId(), stg, admin))
                    .isInstanceOf(ConflictException.class);
            environmentService.merge(project.getId(), stg, staging, admin);

            assertThat(sessionService.get(project.getId(), id).environment()).isEqualTo("staging");
        }
    }
}
