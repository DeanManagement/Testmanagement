export type CommentEntityType = 'TEST_CASE' | 'TEST_RESULT';

export interface Comment {
  id: string;
  content: string;
  authorId: string;
  authorDisplayName: string;
  createdAt: string;
  updatedAt: string;
  createdBy?: string;
  updatedBy?: string;
}

export interface CreateCommentRequest {
  content: string;
}

export interface UpdateCommentRequest {
  content: string;
}
