import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { Router } from '@angular/router';
import { Store } from '@ngrx/store';
import { of } from 'rxjs';
import { catchError, map, mergeMap, tap, withLatestFrom } from 'rxjs/operators';
import { TestPlanApiService } from '../../core/services/test-plan-api.service';
import { TestPlanActions } from './test-plan.actions';
import { selectTestPlanProjectId } from './test-plan.selectors';

@Injectable()
export class TestPlanEffects {
  private readonly actions$ = inject(Actions);
  private readonly testPlanApi = inject(TestPlanApiService);
  private readonly router = inject(Router);
  private readonly store = inject(Store);

  loadTestPlans$ = createEffect(() =>
    this.actions$.pipe(
      ofType(TestPlanActions.loadTestPlans),
      mergeMap(({ projectId }) =>
        this.testPlanApi.getAll(projectId).pipe(
          map((testPlans) => TestPlanActions.loadTestPlansSuccess({ testPlans })),
          catchError((error) =>
            of(TestPlanActions.loadTestPlansFailure({ error: error.message }))
          )
        )
      )
    )
  );

  createTestPlan$ = createEffect(() =>
    this.actions$.pipe(
      ofType(TestPlanActions.createTestPlan),
      mergeMap(({ projectId, request }) =>
        this.testPlanApi.create(projectId, request).pipe(
          map((testPlan) => TestPlanActions.createTestPlanSuccess({ testPlan })),
          catchError((error) =>
            of(TestPlanActions.createTestPlanFailure({ error: error.message }))
          )
        )
      )
    )
  );

  createTestPlanSuccess$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(TestPlanActions.createTestPlanSuccess),
        withLatestFrom(this.store.select(selectTestPlanProjectId)),
        tap(([{ testPlan }, projectId]) =>
          this.router.navigate(['/projects', projectId, 'test-plans', testPlan.id])
        )
      ),
    { dispatch: false }
  );

  updateTestPlan$ = createEffect(() =>
    this.actions$.pipe(
      ofType(TestPlanActions.updateTestPlan),
      mergeMap(({ projectId, id, request }) =>
        this.testPlanApi.update(projectId, id, request).pipe(
          map((testPlan) => TestPlanActions.updateTestPlanSuccess({ testPlan })),
          catchError((error) =>
            of(TestPlanActions.updateTestPlanFailure({ error: error.message }))
          )
        )
      )
    )
  );

  deleteTestPlan$ = createEffect(() =>
    this.actions$.pipe(
      ofType(TestPlanActions.deleteTestPlan),
      mergeMap(({ projectId, id }) =>
        this.testPlanApi.delete(projectId, id).pipe(
          map(() => TestPlanActions.deleteTestPlanSuccess({ id })),
          catchError((error) =>
            of(TestPlanActions.deleteTestPlanFailure({ error: error.message }))
          )
        )
      )
    )
  );

  deleteTestPlanSuccess$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(TestPlanActions.deleteTestPlanSuccess),
        withLatestFrom(this.store.select(selectTestPlanProjectId)),
        tap(([, projectId]) =>
          this.router.navigate(['/projects', projectId, 'test-plans'])
        )
      ),
    { dispatch: false }
  );
}
