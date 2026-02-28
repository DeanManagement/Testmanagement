import { ChangeDetectorRef, Component, inject, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { AsyncPipe, DatePipe, LowerCasePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatInputModule } from '@angular/material/input';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { Observable, of, Subject, Subscription } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
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
import { selectAllComments, selectCommentsLoading } from '../../../store/comment/comment.selectors';
import { selectAuthUser, selectIsSystemAdmin } from '../../../store/auth/auth.selectors';
import { BugReportActions } from '../../../store/bug-report/bug-report.actions';
import { selectLinkedBugReports } from '../../../store/bug-report/bug-report.selectors';
import { ProjectApiService } from '../../../core/services/project-api.service';
import { Comment } from '../../../shared/models/comment.model';
import { CommentListComponent } from '../../../shared/components/comment-list/comment-list.component';
import { CommentFormComponent } from '../../../shared/components/comment-form/comment-form.component';
import { AuthImagePipe } from '../../../shared/pipes/auth-image.pipe';
import { StepSpecCardComponent } from '../../../shared/components/step-spec-card/step-spec-card.component';
import { EntityHistoryComponent } from '../../../shared/components/entity-history/entity-history.component';
import { WatchToggleComponent } from '../../../shared/components/watch-toggle/watch-toggle.component';

@Component({
  selector: 'app-test-run-detail',
  standalone: true,
  imports: [
    AsyncPipe,
    DatePipe,
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
    FormsModule,
    TranslateModule,
    MatTooltipModule,
    CommentListComponent,
    CommentFormComponent,
    AuthImagePipe,
    StepSpecCardComponent,
    EntityHistoryComponent,
    WatchToggleComponent,
  ],
  templateUrl: './test-run-detail.component.html',
  styleUrl: './test-run-detail.component.scss',
})
export class TestRunDetailComponent implements OnInit, OnDestroy {
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);
  private readonly dialog = inject(MatDialog);
  private readonly testRunApi = inject(TestRunApiService);
  private readonly testCaseApi = inject(TestCaseApiService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly projectApi = inject(ProjectApiService);
  private readonly cdr = inject(ChangeDetectorRef);
  private autoSelectSub?: Subscription;
  private actualResultSub?: Subscription;
  private actualResultSubject = new Subject<{ resultId: string; step: StepResult; actualResult: string }>();

  projectId = '';
  runId = '';
  runKey = '';
  testRun$: Observable<TestRun | undefined> = of(undefined);
  resultStatuses: TestResultStatus[] = ['PENDING', 'PASSED', 'FAILED', 'BLOCKED', 'SKIPPED'];

  activeResultId: string | null = null;
  executionSearchTerm = '';

  comments$ = this.store.select(selectAllComments);
  commentsLoading$ = this.store.select(selectCommentsLoading);
  authUser$ = this.store.select(selectAuthUser);
  isAdmin$ = this.store.select(selectIsSystemAdmin);
  editingComment: Comment | null = null;
  bugReportsEnabled = false;
  linkedBugReports$ = this.store.select(selectLinkedBugReports);

  ngOnInit(): void {
    this.projectId = this.route.parent?.snapshot.paramMap.get('id') ?? '';
    this.runId = this.route.snapshot.paramMap.get('runId') ?? '';
    if (this.projectId && this.runId) {
      this.projectApi.getById(this.projectId).subscribe((project) => {
        this.bugReportsEnabled = project.bugReportsEnabled;
        this.cdr.detectChanges();
      });
      this.store.dispatch(TestRunActions.loadTestRun({ projectId: this.projectId, id: this.runId }));
      this.testRun$ = this.store.select(selectTestRunById(this.runId));
      this.autoSelectSub = this.testRun$.subscribe(run => {
        if (run) {
          this.runKey = run.key;
        }
        if (run?.status === 'IN_PROGRESS' && !this.activeResultId && run.results.length > 0) {
          this.activeResultId = run.results[0].id;
          this.loadCommentsForResult(run.results[0].id);
        }
        this.cdr.detectChanges();
      });
    }

    this.actualResultSub = this.actualResultSubject.pipe(debounceTime(500)).subscribe(({ resultId, step, actualResult }) => {
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

  ngOnDestroy(): void {
    this.autoSelectSub?.unsubscribe();
    this.actualResultSub?.unsubscribe();
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
    if (!this.executionSearchTerm) return run.results;
    const term = this.executionSearchTerm.toLowerCase();
    return run.results.filter(r => r.testCaseTitle.toLowerCase().includes(term));
  }

  activeResult(run: TestRun): TestResult | undefined {
    return run.results.find(r => r.id === this.activeResultId);
  }

  setActiveResult(resultId: string): void {
    this.activeResultId = resultId;
    this.editingComment = null;
    this.loadCommentsForResult(resultId);
    if (this.bugReportsEnabled) {
      this.store.dispatch(BugReportActions.loadBugReportsByTestResult({
        projectId: this.projectId,
        testResultId: resultId,
      }));
    }
  }

  activeResultIndex(run: TestRun): number {
    return run.results.findIndex(r => r.id === this.activeResultId);
  }

  canNavigate(direction: 'prev' | 'next', run: TestRun): boolean {
    const idx = this.activeResultIndex(run);
    if (idx === -1) return false;
    return direction === 'prev' ? idx > 0 : idx < run.results.length - 1;
  }

  navigateResult(direction: 'prev' | 'next', run: TestRun): void {
    const idx = this.activeResultIndex(run);
    if (idx === -1) return;
    const newIdx = direction === 'prev' ? idx - 1 : idx + 1;
    if (newIdx >= 0 && newIdx < run.results.length) {
      this.activeResultId = run.results[newIdx].id;
    }
  }

  sortedSteps(result: TestResult): StepResult[] {
    return [...result.stepResults].sort((a, b) => a.orderIndex - b.orderIndex);
  }

  completeRun(run: TestRun): void {
    this.testRunApi.getCompletionInfo(this.projectId, run.id).subscribe(info => {
      this.cdr.detectChanges();
      const dialogRef = this.dialog.open(CompleteTestRunDialogComponent, { data: info });
      dialogRef.afterClosed().subscribe((confirmed: boolean) => {
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
    dialogRef.afterClosed().subscribe((reason: string | undefined) => {
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
    dialogRef.afterClosed().subscribe((result: CloneTestRunDialogResult | undefined) => {
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

  deleteTestRun(id: string): void {
    this.store.dispatch(TestRunActions.deleteTestRun({ projectId: this.projectId, id }));
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
    this.store.dispatch(CommentActions.clearComments());
    this.store.dispatch(CommentActions.loadComments({
      projectId: this.projectId,
      entityType: 'TEST_RESULT',
      entityId: resultId,
      runId: this.runId,
    }));
  }
}
