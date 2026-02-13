import { createActionGroup, emptyProps, props } from '@ngrx/store';
import { Comment, CommentEntityType, CreateCommentRequest, UpdateCommentRequest } from '../../shared/models/comment.model';

export const CommentActions = createActionGroup({
  source: 'Comments',
  events: {
    'Load Comments': props<{ projectId: string; entityType: CommentEntityType; entityId: string; runId?: string }>(),
    'Load Comments Success': props<{ comments: Comment[] }>(),
    'Load Comments Failure': props<{ error: string }>(),
    'Create Comment': props<{ projectId: string; entityType: CommentEntityType; entityId: string; request: CreateCommentRequest; runId?: string }>(),
    'Create Comment Success': props<{ comment: Comment }>(),
    'Create Comment Failure': props<{ error: string }>(),
    'Update Comment': props<{ projectId: string; commentId: string; request: UpdateCommentRequest }>(),
    'Update Comment Success': props<{ comment: Comment }>(),
    'Update Comment Failure': props<{ error: string }>(),
    'Delete Comment': props<{ projectId: string; commentId: string }>(),
    'Delete Comment Success': props<{ id: string }>(),
    'Delete Comment Failure': props<{ error: string }>(),
    'Clear Comments': emptyProps(),
  },
});
