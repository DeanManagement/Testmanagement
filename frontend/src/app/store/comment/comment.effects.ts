import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslateService } from '@ngx-translate/core';
import { Observable, of } from 'rxjs';
import { catchError, map, mergeMap, tap } from 'rxjs/operators';
import { CommentApiService } from '../../core/services/comment-api.service';
import { CommentActions } from './comment.actions';
import { Comment, CommentEntityType, CreateCommentRequest } from '../../shared/models/comment.model';

@Injectable()
export class CommentEffects {
  private readonly actions$ = inject(Actions);
  private readonly commentApi = inject(CommentApiService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly translate = inject(TranslateService);

  loadComments$ = createEffect(() =>
    this.actions$.pipe(
      ofType(CommentActions.loadComments),
      mergeMap(({ projectId, entityType, entityId, runId }) => {
        return this.fetchComments(projectId, entityType, entityId, runId).pipe(
          map((comments) => CommentActions.loadCommentsSuccess({ comments })),
          catchError((error) =>
            of(CommentActions.loadCommentsFailure({ error: error.message }))
          )
        );
      })
    )
  );

  createComment$ = createEffect(() =>
    this.actions$.pipe(
      ofType(CommentActions.createComment),
      mergeMap(({ projectId, entityType, entityId, request, runId }) => {
        return this.postComment(projectId, entityType, entityId, request, runId).pipe(
          map((comment) => CommentActions.createCommentSuccess({ comment })),
          catchError((error) =>
            of(CommentActions.createCommentFailure({ error: error.message }))
          )
        );
      })
    )
  );

  createCommentSnackbar$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(CommentActions.createCommentSuccess),
        tap(() => this.snackBar.open(this.translate.instant('common.savedSuccessfully'), this.translate.instant('common.close'), { duration: 3000 }))
      ),
    { dispatch: false }
  );

  updateComment$ = createEffect(() =>
    this.actions$.pipe(
      ofType(CommentActions.updateComment),
      mergeMap(({ projectId, commentId, request }) =>
        this.commentApi.update(projectId, commentId, request).pipe(
          map((comment) => CommentActions.updateCommentSuccess({ comment })),
          catchError((error) =>
            of(CommentActions.updateCommentFailure({ error: error.message }))
          )
        )
      )
    )
  );

  deleteComment$ = createEffect(() =>
    this.actions$.pipe(
      ofType(CommentActions.deleteComment),
      mergeMap(({ projectId, commentId }) =>
        this.commentApi.delete(projectId, commentId).pipe(
          map(() => CommentActions.deleteCommentSuccess({ id: commentId })),
          catchError((error) =>
            of(CommentActions.deleteCommentFailure({ error: error.message }))
          )
        )
      )
    )
  );

  deleteCommentSnackbar$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(CommentActions.deleteCommentSuccess),
        tap(() => this.snackBar.open(this.translate.instant('common.deletedSuccessfully'), this.translate.instant('common.close'), { duration: 3000 }))
      ),
    { dispatch: false }
  );

  private fetchComments(projectId: string, entityType: CommentEntityType, entityId: string, runId?: string): Observable<Comment[]> {
    switch (entityType) {
      case 'TEST_CASE':
        return this.commentApi.getTestCaseComments(projectId, entityId);
      case 'TEST_RESULT':
        return this.commentApi.getTestResultComments(projectId, runId!, entityId);
      case 'BUG_REPORT':
        return this.commentApi.getBugReportComments(projectId, entityId);
    }
  }

  private postComment(projectId: string, entityType: CommentEntityType, entityId: string,
                      request: CreateCommentRequest, runId?: string): Observable<Comment> {
    switch (entityType) {
      case 'TEST_CASE':
        return this.commentApi.createTestCaseComment(projectId, entityId, request);
      case 'TEST_RESULT':
        return this.commentApi.createTestResultComment(projectId, runId!, entityId, request);
      case 'BUG_REPORT':
        return this.commentApi.createBugReportComment(projectId, entityId, request);
    }
  }
}
