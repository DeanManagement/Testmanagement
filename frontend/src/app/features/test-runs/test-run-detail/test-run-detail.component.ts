import { Component, inject, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { AsyncPipe, DatePipe, LowerCasePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { Observable, of, Subscription } from 'rxjs';
import { TestRunActions } from '../../../store/test-run/test-run.actions';
import { selectTestRunById } from '../../../store/test-run/test-run.selectors';
import { TestRun, TestResult, StepResult, TestResultStatus } from '../../../shared/models/test-run.model';
import { TestRunApiService } from '../../../core/services/test-run-api.service';
import { CloneTestRunDialogComponent, CloneTestRunDialogResult } from '../clone-test-run-dialog/clone-test-run-dialog.component';
import { CompleteTestRunDialogComponent } from '../complete-test-run-dialog/complete-test-run-dialog.component';
import { ReopenTestRunDialogComponent } from '../reopen-test-run-dialog/reopen-test-run-dialog.component';

interface StepChange {
  status?: TestResultStatus;
  actualResult?: string;
}

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
  ],
  templateUrl: './test-run-detail.component.html',
  styleUrl: './test-run-detail.component.scss',
})
export class TestRunDetailComponent implements OnInit, OnDestroy {
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);
  private readonly dialog = inject(MatDialog);
  private readonly testRunApi = inject(TestRunApiService);
  private autoSelectSub?: Subscription;

  projectId = '';
  runId = '';
  testRun$: Observable<TestRun | undefined> = of(undefined);
  resultStatuses: TestResultStatus[] = ['PENDING', 'PASSED', 'FAILED', 'BLOCKED', 'SKIPPED'];

  stepChanges = new Map<string, StepChange>();
  resultChanges = new Map<string, TestResultStatus>();

  activeResultId: string | null = null;
  executionSearchTerm = '';

  ngOnInit(): void {
    this.projectId = this.route.parent?.snapshot.paramMap.get('id') ?? '';
    this.runId = this.route.snapshot.paramMap.get('runId') ?? '';
    if (this.projectId && this.runId) {
      this.store.dispatch(TestRunActions.loadTestRun({ projectId: this.projectId, id: this.runId }));
      this.testRun$ = this.store.select(selectTestRunById(this.runId));
      this.autoSelectSub = this.testRun$.subscribe(run => {
        if (run?.status === 'IN_PROGRESS' && !this.activeResultId && run.results.length > 0) {
          this.activeResultId = run.results[0].id;
        }
      });
    }
  }

  ngOnDestroy(): void {
    this.autoSelectSub?.unsubscribe();
  }

  get hasUnsavedChanges(): boolean {
    return this.stepChanges.size > 0 || this.resultChanges.size > 0;
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

  getResultStatus(result: TestResult): TestResultStatus {
    return this.resultChanges.get(result.id) ?? result.status;
  }

  onResultStatusChange(resultId: string, status: TestResultStatus): void {
    this.resultChanges.set(resultId, status);
  }

  getStepStatus(step: StepResult): TestResultStatus {
    return this.stepChanges.get(step.id)?.status ?? step.status;
  }

  getStepActualResult(step: StepResult): string {
    const change = this.stepChanges.get(step.id);
    return change?.actualResult !== undefined ? change.actualResult : (step.actualResult ?? '');
  }

  onStepStatusChange(stepId: string, status: TestResultStatus): void {
    const existing = this.stepChanges.get(stepId) ?? {};
    this.stepChanges.set(stepId, { ...existing, status });
  }

  onStepActualChange(stepId: string, actualResult: string): void {
    const existing = this.stepChanges.get(stepId) ?? {};
    this.stepChanges.set(stepId, { ...existing, actualResult });
  }

  saveAll(run: TestRun): void {
    for (const [resultId, status] of this.resultChanges) {
      this.store.dispatch(
        TestRunActions.updateTestResult({
          projectId: this.projectId,
          runId: this.runId,
          resultId,
          request: { status },
        })
      );
    }

    for (const result of run.results) {
      for (const step of result.stepResults) {
        const change = this.stepChanges.get(step.id);
        if (change) {
          this.store.dispatch(
            TestRunActions.updateStepResult({
              projectId: this.projectId,
              runId: this.runId,
              resultId: result.id,
              stepResultId: step.id,
              request: {
                status: change.status ?? step.status,
                actualResult: change.actualResult !== undefined ? change.actualResult : (step.actualResult || undefined),
              },
            })
          );
        }
      }
    }

    this.resultChanges.clear();
    this.stepChanges.clear();
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
}
