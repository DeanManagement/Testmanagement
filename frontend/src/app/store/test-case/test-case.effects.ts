import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { Router } from '@angular/router';
import { Store } from '@ngrx/store';
import { of } from 'rxjs';
import { catchError, map, mergeMap, tap, withLatestFrom } from 'rxjs/operators';
import { TestCaseApiService } from '../../core/services/test-case-api.service';
import { TestCaseActions } from './test-case.actions';
import { selectTestCaseProjectId } from './test-case.selectors';

@Injectable()
export class TestCaseEffects {
  private readonly actions$ = inject(Actions);
  private readonly testCaseApi = inject(TestCaseApiService);
  private readonly router = inject(Router);
  private readonly store = inject(Store);

  loadTestCases$ = createEffect(() =>
    this.actions$.pipe(
      ofType(TestCaseActions.loadTestCases),
      mergeMap(({ projectId }) =>
        this.testCaseApi.getAll(projectId).pipe(
          map((testCases) => TestCaseActions.loadTestCasesSuccess({ testCases })),
          catchError((error) =>
            of(TestCaseActions.loadTestCasesFailure({ error: error.message }))
          )
        )
      )
    )
  );

  createTestCase$ = createEffect(() =>
    this.actions$.pipe(
      ofType(TestCaseActions.createTestCase),
      mergeMap(({ projectId, request }) =>
        this.testCaseApi.create(projectId, request).pipe(
          map((testCase) => TestCaseActions.createTestCaseSuccess({ testCase })),
          catchError((error) =>
            of(TestCaseActions.createTestCaseFailure({ error: error.message }))
          )
        )
      )
    )
  );

  createTestCaseSuccess$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(TestCaseActions.createTestCaseSuccess),
        withLatestFrom(this.store.select(selectTestCaseProjectId)),
        tap(([{ testCase }, projectId]) =>
          this.router.navigate(['/projects', projectId, 'test-cases', testCase.id])
        )
      ),
    { dispatch: false }
  );

  updateTestCase$ = createEffect(() =>
    this.actions$.pipe(
      ofType(TestCaseActions.updateTestCase),
      mergeMap(({ projectId, id, request }) =>
        this.testCaseApi.update(projectId, id, request).pipe(
          map((testCase) => TestCaseActions.updateTestCaseSuccess({ testCase })),
          catchError((error) =>
            of(TestCaseActions.updateTestCaseFailure({ error: error.message }))
          )
        )
      )
    )
  );

  deleteTestCase$ = createEffect(() =>
    this.actions$.pipe(
      ofType(TestCaseActions.deleteTestCase),
      mergeMap(({ projectId, id }) =>
        this.testCaseApi.delete(projectId, id).pipe(
          map(() => TestCaseActions.deleteTestCaseSuccess({ id })),
          catchError((error) =>
            of(TestCaseActions.deleteTestCaseFailure({ error: error.message }))
          )
        )
      )
    )
  );

  deleteTestCaseSuccess$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(TestCaseActions.deleteTestCaseSuccess),
        withLatestFrom(this.store.select(selectTestCaseProjectId)),
        tap(([, projectId]) =>
          this.router.navigate(['/projects', projectId, 'test-cases'])
        )
      ),
    { dispatch: false }
  );

  bulkUpdateStatus$ = createEffect(() =>
    this.actions$.pipe(
      ofType(TestCaseActions.bulkUpdateStatus),
      mergeMap(({ projectId, testCaseIds, status }) =>
        this.testCaseApi.bulkUpdateStatus(projectId, testCaseIds, status).pipe(
          map(() => TestCaseActions.bulkUpdateStatusSuccess({ projectId })),
          catchError((error) =>
            of(TestCaseActions.bulkUpdateStatusFailure({ error: error.error?.message || error.message }))
          )
        )
      )
    )
  );

  bulkUpdateStatusSuccess$ = createEffect(() =>
    this.actions$.pipe(
      ofType(TestCaseActions.bulkUpdateStatusSuccess),
      map(({ projectId }) => TestCaseActions.loadTestCases({ projectId }))
    )
  );

  bulkDelete$ = createEffect(() =>
    this.actions$.pipe(
      ofType(TestCaseActions.bulkDelete),
      mergeMap(({ projectId, testCaseIds }) =>
        this.testCaseApi.bulkDelete(projectId, testCaseIds).pipe(
          map(() => TestCaseActions.bulkDeleteSuccess({ projectId })),
          catchError((error) =>
            of(TestCaseActions.bulkDeleteFailure({ error: error.error?.message || error.message }))
          )
        )
      )
    )
  );

  bulkDeleteSuccess$ = createEffect(() =>
    this.actions$.pipe(
      ofType(TestCaseActions.bulkDeleteSuccess),
      map(({ projectId }) => TestCaseActions.loadTestCases({ projectId }))
    )
  );
}
