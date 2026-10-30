export type CommentEntityType = 'TEST_CASE' | 'TEST_RESULT' | 'BUG_REPORT';

export interface Comment {
  id: string;
  content: string;
  authorId: string;
  authorDisplayName: string;
  entityType: CommentEntityType;
  entityId: string;
  createdAt: string;
  updatedAt: string;
  createdBy?: string;
  updatedBy?: string;
}

export interface CreateCommentRequest {
  content: string;
  /** Project members to notify; bug report comments only (TM44). */
  mentionedUserIds?: string[];
}

/** Someone the comment form offers after an "@". */
export interface MentionCandidate {
  userId: string;
  displayName: string;
}

export interface UpdateCommentRequest {
  content: string;
}
