import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslateService } from '@ngx-translate/core';
import { from, Observable, of } from 'rxjs';
import { catchError, concatMap, filter, map, mergeMap, switchMap, tap, toArray } from 'rxjs/operators';
import { BugReportActions } from './bug-report.actions';
import { BugReportApiService } from '../../core/services/bug-report-api.service';
import { AttachmentApiService } from '../../core/services/attachment-api.service';
import { BugReport } from '../../shared/models/bug-report.model';

@Injectable()
export class BugReportEffects {
  private readonly actions$ = inject(Actions);
  private readonly bugReportApi = inject(BugReportApiService);
  private readonly attachmentApi = inject(AttachmentApiService);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);
  private readonly translate = inject(TranslateService);

  loadBugReports$ = createEffect(() =>
    this.actions$.pipe(
      ofType(BugReportActions.loadBugReports),
      switchMap(({ projectId, query }) =>
        this.bugReportApi.getAll(projectId, query).pipe(
          map(({ content, page }) => BugReportActions.loadBugReportsSuccess({ bugReports: content, page })),
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
      mergeMap(({ projectId, request, files }) =>
        this.bugReportApi.create(projectId, request).pipe(
          switchMap((bugReport) => this.uploadQueued(bugReport, files ?? []).pipe(
            map((failedUploads) => BugReportActions.createBugReportSuccess({ bugReport, failedUploads })),
          )),
          catchError((error) => of(BugReportActions.createBugReportFailure({ error: error.message })))
        )
      )
    )
  );

  createBugReportSuccess$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(BugReportActions.createBugReportSuccess),
        tap(({ failedUploads }) => this.snackBar.open(failedUploads?.length
          ? this.translate.instant('attachment.uploadsFailed', { names: failedUploads.join(', ') })
          : this.translate.instant('common.savedSuccessfully'), this.translate.instant('common.close'), { duration: 3000 })),
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
      mergeMap(({ projectId, id, request }) =>
        this.bugReportApi.changeStatus(projectId, id, request).pipe(
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

  /**
   * Uploads the form's queued files one by one after the bug exists (PRD-051), before navigating, so
   * the detail page lists them. A refused file does not undo the bug: its name is reported instead.
   */
  private uploadQueued(bug: BugReport, files: File[]): Observable<string[]> {
    if (!files.length) {
      return of([]);
    }
    const url = this.bugReportApi.attachmentsUrl(bug.projectId, bug.id);
    return from(files).pipe(
      concatMap((file) => this.attachmentApi.uploadQuietly(url, file).pipe(
        map(() => null),
        catchError(() => of(file.name)),
      )),
      filter((name): name is string => name !== null),
      toArray(),
    );
  }
}
