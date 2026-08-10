import { ChangeDetectorRef, Component, DestroyRef, HostListener, inject, OnInit, ViewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { AsyncPipe, LowerCasePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatMenuModule } from '@angular/material/menu';
import { MatDividerModule } from '@angular/material/divider';
import { ConfirmDialogComponent, ConfirmDialogData } from '../../../shared/components/confirm-dialog/confirm-dialog.component';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { Observable, combineLatest, of, Subject } from 'rxjs';
import { debounceTime, take } from 'rxjs/operators';
import { TestRunActions } from '../../../store/test-run/test-run.actions';
import { selectTestRunById } from '../../../store/test-run/test-run.selectors';
import { TestRun, TestResult, StepResult, TestResultStatus } from '../../../shared/models/test-run.model';
import { TestRunApiService } from '../../../core/services/test-run-api.service';
import { AuthService } from '../../../core/services/auth.service';
import { TestCaseApiService } from '../../../core/services/test-case-api.service';
import { CloneTestRunDialogComponent, CloneTestRunDialogResult } from '../clone-test-run-dialog/clone-test-run-dialog.component';
import { CompleteTestRunDialogComponent } from '../complete-test-run-dialog/complete-test-run-dialog.component';
import { ReopenTestRunDialogComponent } from '../reopen-test-run-dialog/reopen-test-run-dialog.component';
import { CommentActions } from '../../../store/comment/comment.actions';
import { selectCommentsForEntity, selectCommentsLoading } from '../../../store/comment/comment.selectors';
import { selectAuthUser, selectIsSystemAdmin } from '../../../store/auth/auth.selectors';
import { BugReportActions } from '../../../store/bug-report/bug-report.actions';
import { selectLinkedBugReportsFor } from '../../../store/bug-report/bug-report.selectors';
import { ProjectApiService } from '../../../core/services/project-api.service';
import { Comment } from '../../../shared/models/comment.model';
import { CommentListComponent } from '../../../shared/components/comment-list/comment-list.component';
import { CommentFormComponent } from '../../../shared/components/comment-form/comment-form.component';
import { KeyboardShortcutsDialogComponent } from '../keyboard-shortcuts-dialog/keyboard-shortcuts-dialog.component';
import { AuthImagePipe } from '../../../shared/pipes/auth-image.pipe';
import { LocalizedDatePipe } from '../../../shared/pipes/localized-date.pipe';
import { RunFailureSummaryComponent } from '../run-failure-summary/run-failure-summary.component';
import { failuresOf, isFailure, worstFirst } from '../../../shared/utils/test-result-triage';
import { StepSpecCardComponent } from '../../../shared/components/step-spec-card/step-spec-card.component';
import { EntityHistoryComponent } from '../../../shared/components/entity-history/entity-history.component';
import { WatchToggleComponent } from '../../../shared/components/watch-toggle/watch-toggle.component';
import { IssueLinksComponent } from '../../../shared/components/issue-links/issue-links.component';
import { IssueTrackerApiService } from '../../../core/services/issue-tracker-api.service';
import { ProjectMemberApiService } from '../../../core/services/project-member-api.service';

@Component({
  selector: 'app-test-run-detail',
  standalone: true,
  imports: [
    AsyncPipe,
    LocalizedDatePipe,
    LowerCasePipe,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatDialogModule,
    MatIconModule,
    MatExpansionModule,
    MatSelectModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    MatMenuModule,
    MatDividerModule,
    MatCheckboxModule,
    FormsModule,
    TranslateModule,
    MatTooltipModule,
    CommentListComponent,
    CommentFormComponent,
    AuthImagePipe,
    StepSpecCardComponent,
    RunFailureSummaryComponent,
    EntityHistoryComponent,
    WatchToggleComponent,
    IssueLinksComponent,
  ],
  templateUrl: './test-run-detail.component.html',
  styleUrl: './test-run-detail.component.scss',
})
export class TestRunDetailComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);
  private readonly dialog = inject(MatDialog);
  private readonly testRunApi = inject(TestRunApiService);
  private readonly testCaseApi = inject(TestCaseApiService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly projectApi = inject(ProjectApiService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);
  private readonly issueTrackerApi = inject(IssueTrackerApiService);
  private readonly memberApi = inject(ProjectMemberApiService);
  private actualResultSubject = new Subject<{ resultId: string; step: StepResult; actualResult: string }>();

  /**
   * Latest snapshot of the test run from the store. Kept on the component
   * so synchronous handlers (keyboard shortcuts) can read it without
   * subscribing inside the handler.
   */
  private currentRun: TestRun | undefined;

  /**
   * Reference to the in-page comment form. The `c` keyboard shortcut calls
   * its `focus()` method so the tester can drop a comment without reaching
   * for the mouse. The view query has `descendants: true` and an explicit
   * filter on the editing-vs-adding form because both render conditionally.
   */
  @ViewChild('addCommentForm') addCommentForm?: CommentFormComponent;

  projectId = '';
  runId = '';
  runKey = '';
  testRun$: Observable<TestRun | undefined> = of(undefined);
  resultStatuses: TestResultStatus[] = ['PENDING', 'PASSED', 'FAILED', 'BLOCKED', 'SKIPPED'];

  activeResultId: string | null = null;
  executionSearchTerm = '';

  // Bulk result-status selection (PRD-008 §2.1)
  bulkMode = false;
  bulkCascade = false;
  readonly selectedResultIds = new Set<string>();

  /**
   * Result IDs whose comments + linked bug reports have been fetched at least
   * once during the current visit to this run. Used to skip re-fetching when
   * the user clicks through the sidebar repeatedly.
   */
  private readonly loadedResultIds = new Set<string>();

  comments$: Observable<Comment[]> = of([]);
  commentsLoading$ = this.store.select(selectCommentsLoading);
  authUser$ = this.store.select(selectAuthUser);
  isAdmin$ = this.store.select(selectIsSystemAdmin);
  editingComment: Comment | null = null;
  bugReportsEnabled = false;
  /** Whether a tracker is configured for this project, so the section is hidden when it is not. */
  issueTrackerConfigured = false;
  /** TESTER and up may link, create and unlink issues; viewers see them read-only. */
  canLinkIssues = false;
  linkedBugReports$: Observable<import('../../../shared/models/bug-report.model').BugReport[]> = of([]);
  uploadProgress$ = this.testRunApi.uploadProgress$;

  ngOnInit(): void {
    this.projectId = this.route.parent?.snapshot.paramMap.get('id') ?? '';
    this.runId = this.route.snapshot.paramMap.get('runId') ?? '';
    if (this.projectId && this.runId) {
      this.projectApi.getById(this.projectId)
        .pipe(take(1), takeUntilDestroyed(this.destroyRef))
        .subscribe((project) => {
          this.bugReportsEnabled = project.bugReportsEnabled;
          this.cdr.detectChanges();
        });
      this.loadIssueTrackerContext();
      this.store.dispatch(TestRunActions.loadTestRun({ projectId: this.projectId, id: this.runId }));
      this.testRun$ = this.store.select(selectTestRunById(this.runId));
      this.testRun$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(run => {
        this.currentRun = run;
        if (run) {
          this.runKey = run.key;
        }
        if (run?.status === 'IN_PROGRESS' && !this.activeResultId && run.results?.length) {
          // Resume on the first unfinished case if there is one; otherwise the first.
          const firstPending = run.results.find(r => r.status === 'PENDING');
          const target = firstPending ?? run.results[0];
          this.setActiveResult(target.id);
        }
        this.cdr.detectChanges();
      });
    }

    this.actualResultSubject.pipe(debounceTime(500), takeUntilDestroyed(this.destroyRef)).subscribe(({ resultId, step, actualResult }) => {
      this.store.dispatch(
        TestRunActions.updateStepResult({
          projectId: this.projectId,
          runId: this.runId,
          resultId,
          stepResultId: step.id,
          request: { status: step.status, actualResult },
        })
      );
    });
  }

  /**
   * Keyboard-driven execution. Bindings are documented in the
   * `KeyboardShortcutsDialogComponent` cheatsheet (press `?` to open).
   *
   * The handler ignores key events when:
   *   - focus is in any input/textarea/contenteditable element (so typing
   *     in the "actual result" field or comment box still works as normal)
   *   - any CDK overlay is open (mat-select, mat-menu, mat-dialog, etc.)
   * This keeps the shortcuts unobtrusive — they only fire when the user is
   * navigating the page, not when they are entering data.
   */
  @HostListener('document:keydown', ['$event'])
  onKeyDown(event: KeyboardEvent): void {
    if (this.shouldIgnoreKeyEvent(event)) {
      return;
    }
    const run = this.currentRun;
    if (!run || run.status !== 'IN_PROGRESS') {
      return;
    }

    switch (event.key) {
      case 'j':
      case 'ArrowDown':
        event.preventDefault();
        this.navigateResult('next', run);
        break;
      case 'k':
      case 'ArrowUp':
        event.preventDefault();
        this.navigateResult('prev', run);
        break;
      case 'p':
        event.preventDefault();
        this.shortcutSetActiveStatus('PASSED');
        break;
      case 'P': // Shift+P
        event.preventDefault();
        this.shortcutMarkAllStepsPassed();
        break;
      case 'f':
      case 'F':
        event.preventDefault();
        this.shortcutSetActiveStatus('FAILED');
        break;
      case 'b':
      case 'B':
        event.preventDefault();
        this.shortcutSetActiveStatus('BLOCKED');
        break;
      case 's':
      case 'S':
        event.preventDefault();
        this.shortcutSetActiveStatus('SKIPPED');
        break;
      case 'c':
      case 'C':
        event.preventDefault();
        this.addCommentForm?.focus();
        break;
      case '?':
        event.preventDefault();
        this.openShortcutsHelp();
        break;
    }
  }

  /** True when the keyboard handler should not intercept the event. */
  private shouldIgnoreKeyEvent(event: KeyboardEvent): boolean {
    if (event.altKey || event.ctrlKey || event.metaKey) {
      return true;
    }
    const target = event.target as HTMLElement | null;
    if (target) {
      if (target.isContentEditable) return true;
      const tag = target.tagName;
      if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return true;
      // Closing the gap: mat-select / mat-menu / mat-dialog all render in a
      // .cdk-overlay-pane. If the user is interacting with one, don't steal
      // their keys.
      if (typeof target.closest === 'function' && target.closest('.cdk-overlay-pane')) {
        return true;
      }
    }
    if (this.dialog.openDialogs.length > 0) return true;
    return false;
  }

  private shortcutSetActiveStatus(status: TestResultStatus): void {
    if (this.activeResultId) {
      this.onResultStatusChange(this.activeResultId, status);
    }
  }

  private shortcutMarkAllStepsPassed(): void {
    const run = this.currentRun;
    if (!run || !this.activeResultId) return;
    const result = run.results?.find(r => r.id === this.activeResultId);
    if (!result) return;
    for (const step of result.stepResults) {
      this.onStepStatusChange(result.id, step, 'PASSED');
    }
    // Also flip the overall result to PASSED so the user only needs one keystroke.
    this.onResultStatusChange(result.id, 'PASSED');
  }

  openShortcutsHelp(): void {
    this.dialog.open(KeyboardShortcutsDialogComponent, { width: '420px' });
  }

  updateStatus(run: TestRun, status: 'IN_PROGRESS' | 'COMPLETED' | 'ABORTED'): void {
    this.store.dispatch(
      TestRunActions.updateTestRun({
        projectId: this.projectId,
        id: run.id,
        request: { name: run.name, environment: run.environment, status },
      })
    );
  }

  onResultStatusChange(resultId: string, status: TestResultStatus): void {
    this.store.dispatch(
      TestRunActions.updateTestResult({
        projectId: this.projectId,
        runId: this.runId,
        resultId,
        request: { status },
      })
    );
  }

  toggleBulkMode(): void {
    this.bulkMode = !this.bulkMode;
    this.selectedResultIds.clear();
  }

  isResultSelected(id: string): boolean {
    return this.selectedResultIds.has(id);
  }

  toggleResultSelection(id: string): void {
    if (this.selectedResultIds.has(id)) {
      this.selectedResultIds.delete(id);
    } else {
      this.selectedResultIds.add(id);
    }
  }

  bulkApply(status: TestResultStatus): void {
    if (this.selectedResultIds.size === 0) {
      return;
    }
    this.testRunApi
      .bulkResultStatus(this.projectId, this.runId, [...this.selectedResultIds], status, this.bulkCascade)
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.selectedResultIds.clear();
        this.bulkMode = false;
        this.store.dispatch(TestRunActions.loadTestRun({ projectId: this.projectId, id: this.runId }));
      });
  }

  onStepStatusChange(resultId: string, step: StepResult, status: TestResultStatus): void {
    this.store.dispatch(
      TestRunActions.updateStepResult({
        projectId: this.projectId,
        runId: this.runId,
        resultId,
        stepResultId: step.id,
        request: { status, actualResult: step.actualResult || undefined },
      })
    );
  }

  onStepActualChange(resultId: string, step: StepResult, actualResult: string): void {
    this.actualResultSubject.next({ resultId, step, actualResult });
  }

  onScreenshotUpload(resultId: string, stepResultId: string, event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (file) {
      this.store.dispatch(
        TestRunActions.uploadScreenshot({
          runId: this.runId,
          resultId,
          stepResultId,
          file,
        })
      );
    }
    input.value = '';
  }

  deleteScreenshot(resultId: string, step: StepResult): void {
    if (step.screenshotId) {
      this.store.dispatch(
        TestRunActions.deleteScreenshot({
          runId: this.runId,
          resultId,
          stepResultId: step.id,
          screenshotId: step.screenshotId,
        })
      );
    }
  }

  getScreenshotUrl(screenshotId: string): string {
    return this.testRunApi.getScreenshotUrl(screenshotId);
  }

  getStepImageUrl(imageId: string): string {
    return this.testCaseApi.getStepImageUrl(imageId);
  }

  filteredResults(run: TestRun): TestResult[] {
    const results = run.results ?? [];
    if (!this.executionSearchTerm) return results;
    const term = this.executionSearchTerm.toLowerCase();
    return results.filter(r => r.testCaseTitle.toLowerCase().includes(term));
  }

  activeResult(run: TestRun): TestResult | undefined {
    return run.results?.find(r => r.id === this.activeResultId);
  }

  setActiveResult(resultId: string): void {
    this.activeResultId = resultId;
    this.editingComment = null;
    this.loadCommentsForResult(resultId);
    this.linkedBugReports$ = this.store.select(selectLinkedBugReportsFor(resultId));
    if (this.bugReportsEnabled && !this.loadedResultIds.has(resultId)) {
      this.store.dispatch(BugReportActions.loadBugReportsByTestResult({
        projectId: this.projectId,
        testResultId: resultId,
      }));
    }
    this.loadedResultIds.add(resultId);
  }

  activeResultIndex(run: TestRun): number {
    return run.results?.findIndex(r => r.id === this.activeResultId) ?? -1;
  }

  canNavigate(direction: 'prev' | 'next', run: TestRun): boolean {
    const idx = this.activeResultIndex(run);
    if (idx === -1) return false;
    const count = run.results?.length ?? 0;
    return direction === 'prev' ? idx > 0 : idx < count - 1;
  }

  navigateResult(direction: 'prev' | 'next', run: TestRun): void {
    const results = run.results ?? [];
    const idx = this.activeResultIndex(run);
    if (idx === -1) return;
    const newIdx = direction === 'prev' ? idx - 1 : idx + 1;
    if (newIdx >= 0 && newIdx < results.length) {
      this.activeResultId = results[newIdx].id;
    }
  }

  sortedSteps(result: TestResult): StepResult[] {
    return [...result.stepResults].sort((a, b) => a.orderIndex - b.orderIndex);
  }

  /** Triage lives in one place so the detail and the report cannot disagree about a count. */
  isFailure = isFailure;

  failures(run: TestRun): TestResult[] {
    return failuresOf(run.results);
  }

  resultsWorstFirst(run: TestRun): TestResult[] {
    return worstFirst(run.results);
  }


  completeRun(run: TestRun): void {
    this.testRunApi.getCompletionInfo(this.projectId, run.id)
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe(info => {
      this.cdr.detectChanges();
      const dialogRef = this.dialog.open(CompleteTestRunDialogComponent, { data: info });
      dialogRef.afterClosed().pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe((confirmed: boolean) => {
        if (confirmed) {
          this.store.dispatch(
            TestRunActions.updateTestRun({
              projectId: this.projectId,
              id: run.id,
              request: { name: run.name, environment: run.environment, status: 'COMPLETED' },
            })
          );
        }
      });
    });
  }

  reopenRun(run: TestRun): void {
    const dialogRef = this.dialog.open(ReopenTestRunDialogComponent);
    dialogRef.afterClosed().pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe((reason: string | undefined) => {
      if (reason) {
        this.store.dispatch(
          TestRunActions.updateTestRun({
            projectId: this.projectId,
            id: run.id,
            request: { name: run.name, environment: run.environment, status: 'IN_PROGRESS', reopenReason: reason },
          })
        );
      }
    });
  }

  cloneTestRun(run: TestRun): void {
    const dialogRef = this.dialog.open(CloneTestRunDialogComponent, {
      data: { name: run.name, environment: run.environment },
    });
    dialogRef.afterClosed().pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe((result: CloneTestRunDialogResult | undefined) => {
      if (result) {
        this.store.dispatch(
          TestRunActions.cloneTestRun({
            projectId: this.projectId,
            runId: run.id,
            request: result,
          })
        );
      }
    });
  }

  deleteTestRun(run: TestRun): void {
    const dialogRef = this.dialog.open(ConfirmDialogComponent, {
      data: {
        titleKey: 'common.delete',
        messageKey: 'testRun.deleteConfirm',
        messageParams: { name: run.name },
        secondaryMessageKey: 'common.irreversibleWarning',
        danger: true,
      } as ConfirmDialogData,
    });
    dialogRef.afterClosed().pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe(confirmed => {
      if (confirmed) {
        this.store.dispatch(TestRunActions.deleteTestRun({ projectId: this.projectId, id: run.id }));
      }
    });
  }

  addResultComment(content: string): void {
    if (this.activeResultId) {
      this.store.dispatch(CommentActions.createComment({
        projectId: this.projectId,
        entityType: 'TEST_RESULT',
        entityId: this.activeResultId,
        request: { content },
        runId: this.runId,
      }));
    }
  }

  startEditComment(comment: Comment): void {
    this.editingComment = comment;
  }

  saveEditComment(content: string): void {
    if (this.editingComment) {
      this.store.dispatch(CommentActions.updateComment({
        projectId: this.projectId,
        commentId: this.editingComment.id,
        request: { content },
      }));
      this.editingComment = null;
    }
  }

  cancelEditComment(): void {
    this.editingComment = null;
  }

  onDeleteComment(comment: Comment): void {
    this.store.dispatch(CommentActions.deleteComment({
      projectId: this.projectId,
      commentId: comment.id,
    }));
  }

  openAllureReport(run: TestRun): void {
    this.router.navigate(['/projects', this.projectId, 'test-runs', run.id, 'allure-report']);
  }

  onAllureReportUpload(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (file) {
      this.store.dispatch(
        TestRunActions.uploadAllureReport({
          projectId: this.projectId,
          testRunId: this.runId,
          testRunKey: this.runKey,
          file,
        })
      );
    }
    input.value = '';
  }

  deleteAllureReport(): void {
    this.store.dispatch(
      TestRunActions.deleteAllureReport({
        projectId: this.projectId,
        testRunId: this.runId,
        testRunKey: this.runKey,
      })
    );
  }

  reportBug(result: TestResult, run: TestRun): void {
    this.router.navigate(['/projects', this.projectId, 'bug-reports', 'new'], {
      queryParams: {
        testResultId: result.id,
        testRunId: run.id,
        testCaseTitle: result.testCaseTitle,
        environment: run.environment || '',
      },
    });
  }

  private loadCommentsForResult(resultId: string): void {
    // Switch the visible thread to this result.
    this.comments$ = this.store.select(selectCommentsForEntity('TEST_RESULT', resultId));
    // Fetch only the first time we land on this result during this visit.
    // Comments for other results we have already seen stay in the store so
    // navigating back to them is instant.
    if (!this.loadedResultIds.has(resultId)) {
      this.store.dispatch(CommentActions.loadComments({
        projectId: this.projectId,
        entityType: 'TEST_RESULT',
        entityId: resultId,
        runId: this.runId,
      }));
    }
  }

  /**
   * Resolves whether this project has a tracker and whether the caller may write to it. Both are
   * needed before the issue section renders: showing "link an issue" on a project with no tracker
   * would only produce a confusing error at the first search.
   */
  private loadIssueTrackerContext(): void {
    this.issueTrackerApi.getStatus(this.projectId)
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (status) => {
          this.issueTrackerConfigured = status.configured;
          this.cdr.detectChanges();
        },
        error: () => undefined,
      });

    combineLatest([
      this.memberApi.getByProject(this.projectId),
      this.store.select(selectAuthUser).pipe(take(1)),
    ])
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ([members, user]) => {
          if (!user) {
            this.canLinkIssues = false;
            return;
          }
          const role = members.find((m) => m.userId === user.id)?.role;
          this.canLinkIssues = user.systemAdmin || role === 'ADMIN' || role === 'TESTER';
          this.cdr.detectChanges();
        },
        error: () => undefined,
      });
  }

}
