import { createFeatureSelector, createSelector } from '@ngrx/store';
import { CommentState, commentAdapter } from './comment.state';

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
