import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { Router } from '@angular/router';
import { Store } from '@ngrx/store';
import { of } from 'rxjs';
import { catchError, map, mergeMap, tap, withLatestFrom } from 'rxjs/operators';
import { TestRunApiService } from '../../core/services/test-run-api.service';
import { TestRunActions } from './test-run.actions';
import { selectTestRunProjectId } from './test-run.selectors';

@Injectable()
export class TestRunEffects {
  private readonly actions$ = inject(Actions);
  private readonly testRunApi = inject(TestRunApiService);
  private readonly router = inject(Router);
  private readonly store = inject(Store);

  loadTestRuns$ = createEffect(() =>
    this.actions$.pipe(
      ofType(TestRunActions.loadTestRuns),
      mergeMap(({ projectId }) =>
        this.testRunApi.getAll(projectId).pipe(
          map((testRuns) => TestRunActions.loadTestRunsSuccess({ testRuns })),
          catchError((error) =>
            of(TestRunActions.loadTestRunsFailure({ error: error.message }))
          )
        )
      )
    )
  );

  loadTestRun$ = createEffect(() =>
    this.actions$.pipe(
      ofType(TestRunActions.loadTestRun),
      mergeMap(({ projectId, id }) =>
        this.testRunApi.getById(projectId, id).pipe(
          map((testRun) => TestRunActions.loadTestRunSuccess({ testRun })),
          catchError((error) =>
            of(TestRunActions.loadTestRunFailure({ error: error.message }))
          )
        )
      )
    )
  );

  createTestRun$ = createEffect(() =>
    this.actions$.pipe(
      ofType(TestRunActions.createTestRun),
      mergeMap(({ projectId, request }) =>
        this.testRunApi.create(projectId, request).pipe(
          map((testRun) => TestRunActions.createTestRunSuccess({ testRun })),
          catchError((error) =>
            of(TestRunActions.createTestRunFailure({ error: error.message }))
          )
        )
      )
    )
  );

  createTestRunSuccess$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(TestRunActions.createTestRunSuccess),
        withLatestFrom(this.store.select(selectTestRunProjectId)),
        tap(([{ testRun }, projectId]) =>
          this.router.navigate(['/projects', projectId, 'test-runs', testRun.id])
        )
      ),
    { dispatch: false }
  );

  updateTestRun$ = createEffect(() =>
    this.actions$.pipe(
      ofType(TestRunActions.updateTestRun),
      mergeMap(({ projectId, id, request }) =>
        this.testRunApi.update(projectId, id, request).pipe(
          map((testRun) => TestRunActions.updateTestRunSuccess({ testRun })),
          catchError((error) =>
            of(TestRunActions.updateTestRunFailure({ error: error.message }))
          )
        )
      )
    )
  );

  deleteTestRun$ = createEffect(() =>
    this.actions$.pipe(
      ofType(TestRunActions.deleteTestRun),
      mergeMap(({ projectId, id }) =>
        this.testRunApi.delete(projectId, id).pipe(
          map(() => TestRunActions.deleteTestRunSuccess({ id })),
          catchError((error) =>
            of(TestRunActions.deleteTestRunFailure({ error: error.message }))
          )
        )
      )
    )
  );

  deleteTestRunSuccess$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(TestRunActions.deleteTestRunSuccess),
        withLatestFrom(this.store.select(selectTestRunProjectId)),
        tap(([, projectId]) =>
          this.router.navigate(['/projects', projectId, 'test-runs'])
        )
      ),
    { dispatch: false }
  );

  updateTestResult$ = createEffect(() =>
    this.actions$.pipe(
      ofType(TestRunActions.updateTestResult),
      mergeMap(({ projectId, runId, resultId, request }) =>
        this.testRunApi.updateResult(projectId, runId, resultId, request).pipe(
          map((result) => TestRunActions.updateTestResultSuccess({ runId, result })),
          catchError((error) =>
            of(TestRunActions.updateTestResultFailure({ error: error.message }))
          )
        )
      )
    )
  );

  updateStepResult$ = createEffect(() =>
    this.actions$.pipe(
      ofType(TestRunActions.updateStepResult),
      mergeMap(({ projectId, runId, resultId, stepResultId, request }) =>
        this.testRunApi.updateStepResult(projectId, runId, resultId, stepResultId, request).pipe(
          map((stepResult) => TestRunActions.updateStepResultSuccess({ runId, resultId, stepResult })),
          catchError((error) =>
            of(TestRunActions.updateStepResultFailure({ error: error.message }))
          )
        )
      )
    )
  );

  refreshAfterStepUpdate$ = createEffect(() =>
    this.actions$.pipe(
      ofType(TestRunActions.updateStepResultSuccess),
      withLatestFrom(this.store.select(selectTestRunProjectId)),
      map(([{ runId }, projectId]) =>
        TestRunActions.loadTestRun({ projectId: projectId!, id: runId })
      )
    )
  );

  uploadScreenshot$ = createEffect(() =>
    this.actions$.pipe(
      ofType(TestRunActions.uploadScreenshot),
      mergeMap(({ runId, resultId, stepResultId, file }) =>
        this.testRunApi.uploadScreenshot(stepResultId, file).pipe(
          map(({ id }) => TestRunActions.uploadScreenshotSuccess({ runId, resultId, stepResultId, screenshotId: id })),
          catchError((error) =>
            of(TestRunActions.uploadScreenshotFailure({ error: error.message }))
          )
        )
      )
    )
  );

  deleteScreenshot$ = createEffect(() =>
    this.actions$.pipe(
      ofType(TestRunActions.deleteScreenshot),
      mergeMap(({ runId, resultId, stepResultId, screenshotId }) =>
        this.testRunApi.deleteScreenshot(screenshotId).pipe(
          map(() => TestRunActions.deleteScreenshotSuccess({ runId, resultId, stepResultId })),
          catchError((error) =>
            of(TestRunActions.deleteScreenshotFailure({ error: error.message }))
          )
        )
      )
    )
  );

  cloneTestRun$ = createEffect(() =>
    this.actions$.pipe(
      ofType(TestRunActions.cloneTestRun),
      mergeMap(({ projectId, runId, request }) =>
        this.testRunApi.clone(projectId, runId, request).pipe(
          map((testRun) => TestRunActions.cloneTestRunSuccess({ testRun })),
          catchError((error) =>
            of(TestRunActions.cloneTestRunFailure({ error: error.message }))
          )
        )
      )
    )
  );

  cloneTestRunSuccess$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(TestRunActions.cloneTestRunSuccess),
        withLatestFrom(this.store.select(selectTestRunProjectId)),
        tap(([{ testRun }, projectId]) =>
          this.router.navigate(['/projects', projectId, 'test-runs', testRun.id])
        )
      ),
    { dispatch: false }
  );

  uploadAllureReport$ = createEffect(() =>
    this.actions$.pipe(
      ofType(TestRunActions.uploadAllureReport),
      mergeMap(({ projectId, testRunId, file }) =>
        this.testRunApi.uploadAllureReport(projectId, testRunId, file).pipe(
          map(() => TestRunActions.uploadAllureReportSuccess({ projectId, testRunId })),
          catchError((error) =>
            of(TestRunActions.uploadAllureReportFailure({ error: error.message }))
          )
        )
      )
    )
  );

  refreshAfterAllureUpload$ = createEffect(() =>
    this.actions$.pipe(
      ofType(TestRunActions.uploadAllureReportSuccess),
      map(({ projectId, testRunId }) =>
        TestRunActions.loadTestRun({ projectId, id: testRunId })
      )
    )
  );

  deleteAllureReport$ = createEffect(() =>
    this.actions$.pipe(
      ofType(TestRunActions.deleteAllureReport),
      mergeMap(({ projectId, testRunId }) =>
        this.testRunApi.deleteAllureReport(projectId, testRunId).pipe(
          map(() => TestRunActions.deleteAllureReportSuccess({ projectId, testRunId })),
          catchError((error) =>
            of(TestRunActions.deleteAllureReportFailure({ error: error.message }))
          )
        )
      )
    )
  );

  refreshAfterAllureDelete$ = createEffect(() =>
    this.actions$.pipe(
      ofType(TestRunActions.deleteAllureReportSuccess),
      map(({ projectId, testRunId }) =>
        TestRunActions.loadTestRun({ projectId, id: testRunId })
      )
    )
  );
}
