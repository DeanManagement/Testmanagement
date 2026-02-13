import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { of } from 'rxjs';
import { catchError, map, mergeMap } from 'rxjs/operators';
import { CommentApiService } from '../../core/services/comment-api.service';
import { CommentActions } from './comment.actions';

@Injectable()
export class CommentEffects {
  private readonly actions$ = inject(Actions);
  private readonly commentApi = inject(CommentApiService);

  loadComments$ = createEffect(() =>
    this.actions$.pipe(
      ofType(CommentActions.loadComments),
      mergeMap(({ projectId, entityType, entityId, runId }) => {
        const request$ = entityType === 'TEST_CASE'
          ? this.commentApi.getTestCaseComments(projectId, entityId)
          : this.commentApi.getTestResultComments(projectId, runId!, entityId);
        return request$.pipe(
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
        const request$ = entityType === 'TEST_CASE'
          ? this.commentApi.createTestCaseComment(projectId, entityId, request)
          : this.commentApi.createTestResultComment(projectId, runId!, entityId, request);
        return request$.pipe(
          map((comment) => CommentActions.createCommentSuccess({ comment })),
          catchError((error) =>
            of(CommentActions.createCommentFailure({ error: error.message }))
          )
        );
      })
    )
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
}
