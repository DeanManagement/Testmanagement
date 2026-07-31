import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { catchError, map, mergeMap, tap } from 'rxjs/operators';
import { BugReportActions } from './bug-report.actions';
import { BugReportApiService } from '../../core/services/bug-report-api.service';

@Injectable()
export class BugReportEffects {
  private readonly actions$ = inject(Actions);
  private readonly bugReportApi = inject(BugReportApiService);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);
  private readonly translate = inject(TranslateService);

  loadBugReports$ = createEffect(() =>
    this.actions$.pipe(
      ofType(BugReportActions.loadBugReports),
      mergeMap(({ projectId }) =>
        this.bugReportApi.getAll(projectId).pipe(
          map((bugReports) => BugReportActions.loadBugReportsSuccess({ bugReports })),
          catchError((error) => of(BugReportActions.loadBugReportsFailure({ error: error.message })))
        )
      )
    )
  );

  loadBugReport$ = createEffect(() =>
    this.actions$.pipe(
      ofType(BugReportActions.loadBugReport),
      mergeMap(({ projectId, id }) =>
        this.bugReportApi.getById(projectId, id).pipe(
          map((bugReport) => BugReportActions.loadBugReportSuccess({ bugReport })),
          catchError((error) => of(BugReportActions.loadBugReportFailure({ error: error.message })))
        )
      )
    )
  );

  loadBugReportsByTestResult$ = createEffect(() =>
    this.actions$.pipe(
      ofType(BugReportActions.loadBugReportsByTestResult),
      mergeMap(({ projectId, testResultId }) =>
        this.bugReportApi.getByTestResult(projectId, testResultId).pipe(
          map((bugReports) => BugReportActions.loadBugReportsByTestResultSuccess({ testResultId, bugReports })),
          catchError((error) => of(BugReportActions.loadBugReportsByTestResultFailure({ error: error.message })))
        )
      )
    )
  );

  createBugReport$ = createEffect(() =>
    this.actions$.pipe(
      ofType(BugReportActions.createBugReport),
      mergeMap(({ projectId, request }) =>
        this.bugReportApi.create(projectId, request).pipe(
          map((bugReport) => BugReportActions.createBugReportSuccess({ bugReport })),
          catchError((error) => of(BugReportActions.createBugReportFailure({ error: error.message })))
        )
      )
    )
  );

  createBugReportSuccess$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(BugReportActions.createBugReportSuccess),
        tap(() => this.snackBar.open(this.translate.instant('common.savedSuccessfully'), this.translate.instant('common.close'), { duration: 3000 })),
        tap(({ bugReport }) =>
          this.router.navigate(['/projects', bugReport.projectId, 'bug-reports', bugReport.id])
        )
      ),
    { dispatch: false }
  );

  updateBugReport$ = createEffect(() =>
    this.actions$.pipe(
      ofType(BugReportActions.updateBugReport),
      mergeMap(({ projectId, id, request }) =>
        this.bugReportApi.update(projectId, id, request).pipe(
          map((bugReport) => BugReportActions.updateBugReportSuccess({ bugReport })),
          catchError((error) => of(BugReportActions.updateBugReportFailure({ error: error.message })))
        )
      )
    )
  );

  updateBugReportSnackbar$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(BugReportActions.updateBugReportSuccess),
        tap(() => this.snackBar.open(this.translate.instant('common.savedSuccessfully'), this.translate.instant('common.close'), { duration: 3000 }))
      ),
    { dispatch: false }
  );

  changeBugReportStatus$ = createEffect(() =>
    this.actions$.pipe(
      ofType(BugReportActions.changeBugReportStatus),
      mergeMap(({ projectId, id, status, reason }) =>
        this.bugReportApi.changeStatus(projectId, id, status, reason).pipe(
          map((bugReport) => BugReportActions.changeBugReportStatusSuccess({ bugReport })),
          catchError((error) => of(BugReportActions.changeBugReportStatusFailure({ error: error.message })))
        )
      )
    )
  );

  changeBugReportStatusSnackbar$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(BugReportActions.changeBugReportStatusSuccess),
        tap(() => this.snackBar.open(this.translate.instant('common.savedSuccessfully'), this.translate.instant('common.close'), { duration: 3000 }))
      ),
    { dispatch: false }
  );

  deleteBugReport$ = createEffect(() =>
    this.actions$.pipe(
      ofType(BugReportActions.deleteBugReport),
      mergeMap(({ projectId, id }) =>
        this.bugReportApi.delete(projectId, id).pipe(
          map(() => BugReportActions.deleteBugReportSuccess({ id })),
          catchError((error) => of(BugReportActions.deleteBugReportFailure({ error: error.message })))
        )
      )
    )
  );

  deleteBugReportSuccess$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(BugReportActions.deleteBugReportSuccess),
        tap(() => this.snackBar.open(this.translate.instant('common.deletedSuccessfully'), this.translate.instant('common.close'), { duration: 3000 })),
        tap(() => history.back())
      ),
    { dispatch: false }
  );

  loadMyBugReports$ = createEffect(() =>
    this.actions$.pipe(
      ofType(BugReportActions.loadMyBugReports),
      mergeMap(() =>
        this.bugReportApi.getAssignedToMe().pipe(
          map((bugReports) => BugReportActions.loadMyBugReportsSuccess({ bugReports })),
          catchError((error) => of(BugReportActions.loadMyBugReportsFailure({ error: error.message })))
        )
      )
    )
  );
}
