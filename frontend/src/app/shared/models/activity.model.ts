export type AuditAction = 'CREATED' | 'UPDATED' | 'DELETED' | 'STATUS_CHANGED' | 'COMPLETED' | 'REOPENED' | 'CLONED';
export type AuditEntityType = 'PROJECT' | 'TEST_CASE' | 'TEST_SUITE' | 'TEST_RUN' | 'TEST_RESULT';

export interface AuditEntry {
  id: string;
  projectId: string;
  userId: string | null;
  userDisplayName: string | null;
  action: AuditAction;
  entityType: AuditEntityType;
  entityId: string | null;
  entityName: string | null;
  details: string | null;
  createdAt: string;
}

export interface PagedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  last: boolean;
}
