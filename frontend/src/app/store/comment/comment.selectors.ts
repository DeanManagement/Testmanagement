import { createFeatureSelector, createSelector } from '@ngrx/store';
import { CommentState, commentAdapter } from './comment.state';
import { CommentEntityType } from '../../shared/models/comment.model';

export const selectCommentState = createFeatureSelector<CommentState>('comments');

const { selectAll } = commentAdapter.getSelectors();

export const selectAllComments = createSelector(selectCommentState, selectAll);

export const selectCommentsLoading = createSelector(
  selectCommentState,
  (state) => state.loading
);

export const selectCommentsError = createSelector(
  selectCommentState,
  (state) => state.error
);

/**
 * Returns the chronological comment thread for a single entity (e.g. one
 * TEST_RESULT). Use this when the store may hold comments for several
 * entities at once — for example while the user clicks through results in
 * a test run — so each view shows only its own thread.
 */
export const selectCommentsForEntity = (entityType: CommentEntityType, entityId: string | null) =>
  createSelector(selectAllComments, (comments) =>
    entityId
      ? comments
          .filter((c) => c.entityType === entityType && c.entityId === entityId)
          .sort((a, b) => a.createdAt.localeCompare(b.createdAt))
      : []
  );
