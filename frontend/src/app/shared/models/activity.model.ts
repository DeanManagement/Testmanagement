export type AuditAction = 'CREATED' | 'UPDATED' | 'DELETED' | 'STATUS_CHANGED' | 'COMPLETED' | 'REOPENED' | 'CLONED' | 'MOVED';
export type AuditEntityType = 'PROJECT' | 'TEST_CASE' | 'TEST_SUITE' | 'TEST_RUN' | 'TEST_RESULT' | 'COMMENT' | 'TEST_PLAN'
  | 'BUG_REPORT' | 'TEST_CASE_FOLDER' | 'REQUIREMENT' | 'ENVIRONMENT' | 'EXPLORATORY_SESSION' | 'SHARED_STEP' | 'ATTACHMENT';

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
  /** PRD-046: empty for entries written before field changes were recorded. */
  changes: FieldChange[];
  parentEntityType: AuditEntityType | null;
  parentEntityId: string | null;
  /** Where the entry leads; null once the object is deleted, or when it has no page. */
  link: AuditLink | null;
}

/** One changed field; null means the field was empty. */
export interface FieldChange {
  field: string;
  from: string | null;
  to: string | null;
}

export interface AuditLink {
  type: AuditEntityType;
  id: string;
}

export const ALL_AUDIT_ACTIONS: AuditAction[] =
  ['CREATED', 'UPDATED', 'DELETED', 'STATUS_CHANGED', 'COMPLETED', 'REOPENED', 'CLONED', 'MOVED'];

/** The object types worth filtering the activity by; the rest are rare or internal. */
export const FILTERABLE_ENTITY_TYPES: AuditEntityType[] = ['TEST_CASE', 'TEST_SUITE', 'TEST_RUN', 'TEST_PLAN',
  'BUG_REPORT', 'COMMENT', 'REQUIREMENT', 'EXPLORATORY_SESSION', 'SHARED_STEP', 'ATTACHMENT', 'PROJECT'];

/** Activity filters as the URL and the API carry them (PRD-046). from/to are ISO instants. */
export interface ActivityQuery {
  entityId?: string;
  entityType?: AuditEntityType[];
  userId?: string[];
  action?: AuditAction[];
  from?: string;
  to?: string;
  sort?: 'asc' | 'desc';
  page?: number;
  size?: number;
}
