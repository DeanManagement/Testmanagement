import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { Router } from '@angular/router';
import { Store } from '@ngrx/store';
import { of } from 'rxjs';
import { catchError, map, mergeMap, tap, withLatestFrom } from 'rxjs/operators';
import { TestSuiteApiService } from '../../core/services/test-suite-api.service';
import { TestSuiteActions } from './test-suite.actions';
import { selectTestSuiteProjectId } from './test-suite.selectors';

@Injectable()
export class TestSuiteEffects {
  private readonly actions$ = inject(Actions);
  private readonly testSuiteApi = inject(TestSuiteApiService);
  private readonly router = inject(Router);
  private readonly store = inject(Store);

  loadTestSuites$ = createEffect(() =>
    this.actions$.pipe(
      ofType(TestSuiteActions.loadTestSuites),
      mergeMap(({ projectId }) =>
        this.testSuiteApi.getAll(projectId).pipe(
          map((testSuites) => TestSuiteActions.loadTestSuitesSuccess({ testSuites })),
          catchError((error) =>
            of(TestSuiteActions.loadTestSuitesFailure({ error: error.message }))
          )
        )
      )
    )
  );

  createTestSuite$ = createEffect(() =>
    this.actions$.pipe(
      ofType(TestSuiteActions.createTestSuite),
      mergeMap(({ projectId, request }) =>
        this.testSuiteApi.create(projectId, request).pipe(
          map((testSuite) => TestSuiteActions.createTestSuiteSuccess({ testSuite })),
          catchError((error) =>
            of(TestSuiteActions.createTestSuiteFailure({ error: error.message }))
          )
        )
      )
    )
  );

  createTestSuiteSuccess$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(TestSuiteActions.createTestSuiteSuccess),
        withLatestFrom(this.store.select(selectTestSuiteProjectId)),
        tap(([{ testSuite }, projectId]) =>
          this.router.navigate(['/projects', projectId, 'test-suites', testSuite.id])
        )
      ),
    { dispatch: false }
  );

  updateTestSuite$ = createEffect(() =>
    this.actions$.pipe(
      ofType(TestSuiteActions.updateTestSuite),
      mergeMap(({ projectId, id, request }) =>
        this.testSuiteApi.update(projectId, id, request).pipe(
          map((testSuite) => TestSuiteActions.updateTestSuiteSuccess({ testSuite })),
          catchError((error) =>
            of(TestSuiteActions.updateTestSuiteFailure({ error: error.message }))
          )
        )
      )
    )
  );

  deleteTestSuite$ = createEffect(() =>
    this.actions$.pipe(
      ofType(TestSuiteActions.deleteTestSuite),
      mergeMap(({ projectId, id }) =>
        this.testSuiteApi.delete(projectId, id).pipe(
          map(() => TestSuiteActions.deleteTestSuiteSuccess({ id })),
          catchError((error) =>
            of(TestSuiteActions.deleteTestSuiteFailure({ error: error.message }))
          )
        )
      )
    )
  );

  deleteTestSuiteSuccess$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(TestSuiteActions.deleteTestSuiteSuccess),
        withLatestFrom(this.store.select(selectTestSuiteProjectId)),
        tap(([, projectId]) =>
          this.router.navigate(['/projects', projectId, 'test-suites'])
        )
      ),
    { dispatch: false }
  );
}
