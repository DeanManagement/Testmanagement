import { EntityState, createEntityAdapter } from '@ngrx/entity';
import { Comment } from '../../shared/models/comment.model';

export const commentAdapter = createEntityAdapter<Comment>();

export interface CommentState extends EntityState<Comment> {
  loading: boolean;
  error: string | null;
}

export const initialCommentState: CommentState = commentAdapter.getInitialState({
  loading: false,
  error: null,
});
