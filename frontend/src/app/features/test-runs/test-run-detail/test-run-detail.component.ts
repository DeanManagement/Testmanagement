import { ChangeDetectorRef, Component, DestroyRef, HostListener, inject, OnInit, signal, ViewChild } from '@angular/core';
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
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Observable, combineLatest, interval, of, Subject } from 'rxjs';
import { debounceTime, filter, switchMap, take } from 'rxjs/operators';
import { TestRunActions } from '../../../store/test-run/test-run.actions';
import { selectTestRunById } from '../../../store/test-run/test-run.selectors';
import { TestRun, TestResult, StepResult, TestResultStatus } from '../../../shared/models/test-run.model';
import { TestRunApiService } from '../../../core/services/test-run-api.service';
import { AuthService } from '../../../core/services/auth.service';
import { TestCaseApiService } from '../../../core/services/test-case-api.service';
import { CloneTestRunDialogComponent, CloneTestRunDialogResult } from '../clone-test-run-dialog/clone-test-run-dialog.component';
import { CompleteTestRunDialogComponent } from '../complete-test-run-dialog/complete-test-run-dialog.component';
import { ReasonDialogData, ReopenTestRunDialogComponent } from '../reopen-test-run-dialog/reopen-test-run-dialog.component';
import { CommentActions } from '../../../store/comment/comment.actions';
import { selectCommentsForEntity, selectCommentsLoading } from '../../../store/comment/comment.selectors';
import { selectAuthUser, selectIsSystemAdmin } from '../../../store/auth/auth.selectors';
import { BugReportActions } from '../../../store/bug-report/bug-report.actions';
import { BugReportApiService } from '../../../core/services/bug-report-api.service';
import { BugReport } from '../../../shared/models/bug-report.model';
import { LinkBugDialogComponent, LinkBugDialogData } from '../../bug-reports/link-bug-dialog/link-bug-dialog.component';
import { cascadableSteps, hasFailure, inStatus, isFailureStatus, stepSeenAt } from './result-defects';
import { CaseContextComponent } from './case-context.component';
import { selectLinkedBugReportsFor } from '../../../store/bug-report/bug-report.selectors';
import { ProjectApiService } from '../../../core/services/project-api.service';
import { Comment } from '../../../shared/models/comment.model';
import { CommentListComponent } from '../../../shared/components/comment-list/comment-list.component';
import { CommentFormComponent } from '../../../shared/components/comment-form/comment-form.component';
import { KeyboardShortcutsDialogComponent } from '../keyboard-shortcuts-dialog/keyboard-shortcuts-dialog.component';
import { AuthImagePipe } from '../../../shared/pipes/auth-image.pipe';
import { LocalizedDatePipe } from '../../../shared/pipes/localized-date.pipe';
import { failuresOf, isFailure, worstFirst } from '../../../shared/utils/test-result-triage';
import { StepSpecCardComponent } from '../../../shared/components/step-spec-card/step-spec-card.component';
import { EntityHistoryComponent } from '../../../shared/components/entity-history/entity-history.component';
import { WatchToggleComponent } from '../../../shared/components/watch-toggle/watch-toggle.component';
import { IssueLinksComponent } from '../../../shared/components/issue-links/issue-links.component';
import { AttachmentsComponent } from '../../../shared/components/attachments/attachments.component';
import { IssueTrackerApiService } from '../../../core/services/issue-tracker-api.service';
import { ProjectMemberApiService } from '../../../core/services/project-member-api.service';
import { CustomFieldsDisplayComponent } from '../../../shared/components/custom-fields/custom-fields-display.component';
import { ExecutionTimer, LONG_DURATION_MS } from './execution-timer';
import { effortOf, millisToMinutes, minutesToMillis } from '../../../shared/pipes/duration';
import { DurationPipe } from '../../../shared/pipes/duration.pipe';
import { EffortSummary } from '../../../shared/models/effort.model';

/** How often the running time on screen advances; it shows whole minutes. */
const TIMER_TICK_MS = 15_000;

import { sharedStepHeadingAt } from '../../../shared/utils/shared-step-groups';
@Component({
  selector: 'app-test-run-detail',
  standalone: true,
  imports: [
    CaseContextComponent,
    DurationPipe,
    CustomFieldsDisplayComponent,
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
    EntityHistoryComponent,
    WatchToggleComponent,
    IssueLinksComponent,
    AttachmentsComponent,
  ],
  templateUrl: './test-run-detail.component.html',
  styleUrl: './test-run-detail.component.scss',
})
export class TestRunDetailComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);
  private readonly dialog = inject(MatDialog);
  private readonly bugReportApi = inject(BugReportApiService);
  private readonly translate = inject(TranslateService);
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

  /** A result named in the URL (?result=), opened and scrolled to once the run has loaded. */
  linkedResultId: string | null = null;
  private linkedResultShown = false;

  /** PRD-036: times each result from when it is opened; see ExecutionTimer for what gets sent. */
  private readonly timer = new ExecutionTimer();
  /** A duration the tester typed for the active result, in minutes; null means "use the timer". */
  durationInputMinutes: number | null = null;
  /** Ticks while executing so the running time on screen advances; minutes, so 15 s is plenty. */
  readonly now = signal(Date.now());
  executionSearchTerm = '';
  /** PRD-047: from ?status=, e.g. the plan's Failed count; only results in this status are listed. */
  resultStatusFilter: TestResultStatus | null = null;

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
    this.linkedResultId = this.route.snapshot.queryParamMap.get('result');
    this.resultStatusFilter = this.route.snapshot.queryParamMap.get('status') as TestResultStatus | null;
    interval(TIMER_TICK_MS).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      if (this.currentRun?.status === 'IN_PROGRESS') {
        this.now.set(Date.now());
      }
    });
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
          // A linked result (?result=, e.g. from a run comparison) first; else resume on the first
          // unfinished case; else the first.
          const linked = run.results.find(r => r.id === this.linkedResultId);
          const firstPending = run.results.find(r => r.status === 'PENDING');
          const target = linked ?? firstPending ?? run.results[0];
          this.setActiveResult(target.id);
        }
        this.scrollToLinkedResult(run);
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

  /** Aborting ends the run, so it asks first and records why (bug report efb94f3f). */
  abortRun(run: TestRun): void {
    this.dialog.open(ReopenTestRunDialogComponent, { data: { action: 'abort' } as ReasonDialogData })
      .afterClosed().pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe((reason: string | undefined) => {
        if (reason) {
          this.store.dispatch(TestRunActions.updateTestRun({
            projectId: this.projectId,
            id: run.id,
            request: { name: run.name, environment: run.environment, status: 'ABORTED', abortReason: reason },
          }));
        }
      });
  }

  updateStatus(run: TestRun, status: 'IN_PROGRESS' | 'COMPLETED'): void {
    this.store.dispatch(
      TestRunActions.updateTestRun({
        projectId: this.projectId,
        id: run.id,
        request: { name: run.name, environment: run.environment, status },
      })
    );
  }

  /** PRD-048: after a pass or skip, the offer to carry it down to the steps still pending. */
  cascadeOffer: { resultId: string; status: TestResultStatus; count: number } | null = null;

  /** A status picked by the tester; a pass or skip then offers to cascade to pending steps. */
  chooseResultStatus(result: TestResult, status: TestResultStatus): void {
    this.onResultStatusChange(result.id, status);
    const count = cascadableSteps(result, status);
    this.cascadeOffer = count > 0 ? { resultId: result.id, status, count } : null;
  }

  acceptCascade(): void {
    const offer = this.cascadeOffer;
    this.cascadeOffer = null;
    if (!offer) return;
    this.store.dispatch(TestRunActions.updateTestResult({
      projectId: this.projectId,
      runId: this.runId,
      resultId: offer.resultId,
      request: { status: offer.status, cascadeSteps: true },
    }));
  }

  onResultStatusChange(resultId: string, status: TestResultStatus): void {
    const result = this.currentRun?.results?.find(r => r.id === resultId);
    // Only the result open in the panel is timed; bulk and list changes record no duration rather than a fake one.
    const durationMs = status === 'PENDING' || resultId !== this.activeResultId || !result
      ? undefined
      : this.timer.durationFor(resultId, result.durationMs, this.typedDurationMs(result));
    this.confirmLongDuration(durationMs, (confirmed) => this.dispatchResultUpdate(resultId, status, confirmed));
  }

  /** Saves an edited duration on a result that already has a status (before that, it goes with the status). */
  saveDuration(result: TestResult): void {
    const typed = this.typedDurationMs(result);
    if (result.status === 'PENDING' || typed === null || typed === result.durationMs) {
      return;
    }
    this.confirmLongDuration(typed, (confirmed) => {
      if (confirmed !== undefined) {
        this.dispatchResultUpdate(result.id, result.status, confirmed);
      }
    });
  }

  /** Running time of the active result, in whole minutes, for the field's placeholder. */
  runningMinutes(resultId: string): number {
    this.now();
    return millisToMinutes(this.timer.elapsedMs(resultId) ?? 0);
  }

  runEffort(run: TestRun): EffortSummary {
    return effortOf(run.results ?? []);
  }

  private typedDurationMs(result: TestResult): number | null {
    if (this.durationInputMinutes == null || this.durationInputMinutes < 0) {
      return null;
    }
    const typed = minutesToMillis(this.durationInputMinutes);
    // The field starts out showing the recorded value; unchanged, it is not a manual edit.
    return result.durationMs !== null && millisToMinutes(result.durationMs) === this.durationInputMinutes ? null : typed;
  }

  /** Over 8 h is probably a timer left running: ask, and on "no" record the status without it. */
  private confirmLongDuration(durationMs: number | undefined, proceed: (durationMs: number | undefined) => void): void {
    if (durationMs === undefined || durationMs <= LONG_DURATION_MS) {
      proceed(durationMs);
      return;
    }
    const data: ConfirmDialogData = {
      titleKey: 'timeTracking.longDuration.title',
      messageKey: 'timeTracking.longDuration.message',
      messageParams: { minutes: millisToMinutes(durationMs) },
    };
    this.dialog.open(ConfirmDialogComponent, { data }).afterClosed()
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe((confirmed) => proceed(confirmed ? durationMs : undefined));
  }

  private dispatchResultUpdate(resultId: string, status: TestResultStatus, durationMs: number | undefined): void {
    this.store.dispatch(
      TestRunActions.updateTestResult({
        projectId: this.projectId,
        runId: this.runId,
        resultId,
        request: durationMs === undefined ? { status } : { status, durationMs },
      })
    );
    if (resultId === this.activeResultId && durationMs !== undefined) {
      this.durationInputMinutes = millisToMinutes(durationMs);
    }
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
    const results = this.byStatusFilter(run.results ?? []);
    if (!this.executionSearchTerm) return results;
    const term = this.executionSearchTerm.toLowerCase();
    return results.filter(r => r.testCaseTitle.toLowerCase().includes(term) || r.testCaseKey?.toLowerCase().includes(term));
  }

  private byStatusFilter(results: TestResult[]): TestResult[] {
    return inStatus(results, this.resultStatusFilter);
  }

  clearStatusFilter(): void {
    this.resultStatusFilter = null;
    this.router.navigate([], { relativeTo: this.route, queryParams: { status: null }, queryParamsHandling: 'merge', replaceUrl: true });
  }

  /** Once per visit: a finished run's linked panel is expanded by the template; bring it into view. */
  private scrollToLinkedResult(run: TestRun | undefined): void {
    if (!this.linkedResultId || this.linkedResultShown || !run?.results?.some(r => r.id === this.linkedResultId)) {
      return;
    }
    this.linkedResultShown = true;
    const testId = run.status === 'IN_PROGRESS' ? 'execution-main' : 'test-result-panel-' + this.linkedResultId;
    // After this change-detection pass has rendered the panel.
    setTimeout(() => document.querySelector(`[data-test-id="${testId}"]`)?.scrollIntoView({ block: 'center' }));
  }

  activeResult(run: TestRun): TestResult | undefined {
    return run.results?.find(r => r.id === this.activeResultId);
  }

  setActiveResult(resultId: string): void {
    this.activeResultId = resultId;
    this.cascadeOffer = null;
    if (this.currentRun?.status === 'IN_PROGRESS') {
      this.timer.open(resultId);
    }
    const recorded = this.currentRun?.results?.find(r => r.id === resultId)?.durationMs ?? null;
    this.durationInputMinutes = recorded === null ? null : millisToMinutes(recorded);
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

  /** PRD-030: a heading above the first step of each shared step. */
  readonly headingAt = sharedStepHeadingAt;

  sortedSteps(result: TestResult): StepResult[] {
    return [...result.stepResults].sort((a, b) => a.orderIndex - b.orderIndex);
  }

  /** Triage lives in one place so the detail and the report cannot disagree about a count. */
  isFailure = isFailure;

  failures(run: TestRun): TestResult[] {
    return failuresOf(run.results);
  }

  resultsWorstFirst(run: TestRun): TestResult[] {
    return worstFirst(this.byStatusFilter(run.results ?? []));
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
      data: { projectId: this.projectId, name: run.name, environment: run.environment ?? '' },
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

  readonly hasFailure = hasFailure;
  readonly isStepFailure = isFailureStatus;
  readonly stepSeenAt = stepSeenAt;

  /** With a step, it is named and its actual result becomes the bug's actual behaviour. */
  reportBug(result: TestResult, run: TestRun, step?: StepResult): void {
    const number = step ? this.sortedSteps(result).indexOf(step) + 1 : null;
    this.router.navigate(['/projects', this.projectId, 'bug-reports', 'new'], {
      queryParams: {
        testResultId: result.id,
        testRunId: run.id,
        testCaseTitle: result.testCaseTitle,
        environment: run.environment || '',
        stepResultId: step?.id,
        stepsToReproduce: step ? this.translate.instant('bugReport.fromStep', { number, action: step.action }) : undefined,
        actualBehavior: step?.actualResult || undefined,
      },
    });
  }

  /** PRD-047: the bug showed up again here. Bugs already on the result are not offered. */
  linkBug(result: TestResult, step?: StepResult): void {
    this.store.select(selectLinkedBugReportsFor(result.id)).pipe(
      take(1),
      switchMap((linked) => this.dialog.open(LinkBugDialogComponent, {
        data: { projectId: this.projectId, excludeIds: linked.map(bug => bug.id) } as LinkBugDialogData,
      }).afterClosed()),
      filter((bug): bug is BugReport => !!bug),
      switchMap((bug) => this.bugReportApi.link(this.projectId, bug.id, result.id, step?.id)),
      take(1),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(() => this.store.dispatch(BugReportActions.loadBugReportsByTestResult({
      projectId: this.projectId,
      testResultId: result.id,
    })));
  }

  /** Saved on leaving the field; an empty field clears the link. */
  saveDefectLink(result: TestResult, value: string): void {
    const defectLink = value.trim();
    if (defectLink === (result.defectLink ?? '')) return;
    this.store.dispatch(TestRunActions.updateTestResult({
      projectId: this.projectId,
      runId: this.runId,
      resultId: result.id,
      request: { status: result.status, defectLink },
    }));
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
