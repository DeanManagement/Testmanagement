import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { of } from 'rxjs';
import { catchError, map, mergeMap } from 'rxjs/operators';
import { TestCaseFolderApiService } from '../../core/services/test-case-folder-api.service';
import { TestCaseFolderActions } from './test-case-folder.actions';
import { TestCaseActions } from '../test-case/test-case.actions';

@Injectable()
export class TestCaseFolderEffects {
  private readonly actions$ = inject(Actions);
  private readonly folderApi = inject(TestCaseFolderApiService);

  loadFolders$ = createEffect(() =>
    this.actions$.pipe(
      ofType(TestCaseFolderActions.loadFolders),
      mergeMap(({ projectId }) =>
        this.folderApi.getTree(projectId).pipe(
          map((folders) => TestCaseFolderActions.loadFoldersSuccess({ folders })),
          catchError((error) =>
            of(TestCaseFolderActions.loadFoldersFailure({ error: error.message }))
          )
        )
      )
    )
  );

  createFolder$ = createEffect(() =>
    this.actions$.pipe(
      ofType(TestCaseFolderActions.createFolder),
      mergeMap(({ projectId, request }) =>
        this.folderApi.create(projectId, request).pipe(
          map(() => TestCaseFolderActions.createFolderSuccess({ projectId })),
          catchError((error) =>
            of(TestCaseFolderActions.createFolderFailure({ error: error.message }))
          )
        )
      )
    )
  );

  createFolderSuccess$ = createEffect(() =>
    this.actions$.pipe(
      ofType(TestCaseFolderActions.createFolderSuccess),
      map(({ projectId }) => TestCaseFolderActions.loadFolders({ projectId }))
    )
  );

  updateFolder$ = createEffect(() =>
    this.actions$.pipe(
      ofType(TestCaseFolderActions.updateFolder),
      mergeMap(({ projectId, folderId, request }) =>
        this.folderApi.update(projectId, folderId, request).pipe(
          map(() => TestCaseFolderActions.updateFolderSuccess({ projectId })),
          catchError((error) =>
            of(TestCaseFolderActions.updateFolderFailure({ error: error.message }))
          )
        )
      )
    )
  );

  updateFolderSuccess$ = createEffect(() =>
    this.actions$.pipe(
      ofType(TestCaseFolderActions.updateFolderSuccess),
      map(({ projectId }) => TestCaseFolderActions.loadFolders({ projectId }))
    )
  );

  deleteFolder$ = createEffect(() =>
    this.actions$.pipe(
      ofType(TestCaseFolderActions.deleteFolder),
      mergeMap(({ projectId, folderId }) =>
        this.folderApi.delete(projectId, folderId).pipe(
          map(() => TestCaseFolderActions.deleteFolderSuccess({ projectId })),
          catchError((error) =>
            of(TestCaseFolderActions.deleteFolderFailure({ error: error.message }))
          )
        )
      )
    )
  );

  deleteFolderSuccess$ = createEffect(() =>
    this.actions$.pipe(
      ofType(TestCaseFolderActions.deleteFolderSuccess),
      mergeMap(({ projectId }) => [
        TestCaseFolderActions.loadFolders({ projectId }),
        TestCaseActions.loadTestCases({ projectId }),
      ])
    )
  );

  reorderFolders$ = createEffect(() =>
    this.actions$.pipe(
      ofType(TestCaseFolderActions.reorderFolders),
      mergeMap(({ projectId, request }) =>
        this.folderApi.reorder(projectId, request).pipe(
          map((folders) => TestCaseFolderActions.reorderFoldersSuccess({ folders })),
          catchError((error) =>
            of(TestCaseFolderActions.reorderFoldersFailure({ error: error.message }))
          )
        )
      )
    )
  );

  moveTestCases$ = createEffect(() =>
    this.actions$.pipe(
      ofType(TestCaseFolderActions.moveTestCases),
      mergeMap(({ projectId, request }) =>
        this.folderApi.moveTestCases(projectId, request).pipe(
          map(() => TestCaseFolderActions.moveTestCasesSuccess({ projectId })),
          catchError((error) =>
            of(TestCaseFolderActions.moveTestCasesFailure({ error: error.message }))
          )
        )
      )
    )
  );

  moveTestCasesSuccess$ = createEffect(() =>
    this.actions$.pipe(
      ofType(TestCaseFolderActions.moveTestCasesSuccess),
      mergeMap(({ projectId }) => [
        TestCaseFolderActions.loadFolders({ projectId }),
        TestCaseActions.loadTestCases({ projectId }),
      ])
    )
  );
}
