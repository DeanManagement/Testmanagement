import { createReducer, on } from '@ngrx/store';
import { CommentActions } from './comment.actions';
import { initialCommentState, commentAdapter } from './comment.state';

export const commentReducer = createReducer(
  initialCommentState,

  on(CommentActions.loadComments, (state) => ({
    ...state,
    loading: true,
    error: null,
  })),

  on(CommentActions.loadCommentsSuccess, (state, { comments }) =>
    commentAdapter.setAll(comments, { ...state, loading: false })
  ),

  on(CommentActions.loadCommentsFailure, (state, { error }) => ({
    ...state,
    loading: false,
    error,
  })),

  on(CommentActions.createCommentSuccess, (state, { comment }) =>
    commentAdapter.addOne(comment, state)
  ),

  on(CommentActions.createCommentFailure, (state, { error }) => ({
    ...state,
    error,
  })),

  on(CommentActions.updateCommentSuccess, (state, { comment }) =>
    commentAdapter.upsertOne(comment, state)
  ),

  on(CommentActions.updateCommentFailure, (state, { error }) => ({
    ...state,
    error,
  })),

  on(CommentActions.deleteCommentSuccess, (state, { id }) =>
    commentAdapter.removeOne(id, state)
  ),

  on(CommentActions.deleteCommentFailure, (state, { error }) => ({
    ...state,
    error,
  })),

  on(CommentActions.clearComments, () => initialCommentState)
);
