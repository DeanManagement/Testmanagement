import { CustomFieldValues } from './custom-field.model';

/** PRD-045: NEW is reported but not yet triaged; OPEN is confirmed. */
export type BugReportStatus = 'NEW' | 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED';

export const ALL_BUG_STATUSES: BugReportStatus[] = ['NEW', 'OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'];

/** Why a bug was closed: required for CLOSED, optional for RESOLVED. */
export type BugResolution = 'FIXED' | 'WONT_FIX' | 'DUPLICATE' | 'CANNOT_REPRODUCE' | 'NOT_A_BUG' | 'DEFERRED';

export const ALL_BUG_RESOLUTIONS: BugResolution[] =
  ['FIXED', 'WONT_FIX', 'DUPLICATE', 'CANNOT_REPRODUCE', 'NOT_A_BUG', 'DEFERRED'];

/** Statuses that take a resolution; the others clear it. */
export function takesResolution(status: BugReportStatus): boolean {
  return status === 'RESOLVED' || status === 'CLOSED';
}

export type Priority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export interface BugReport {
  id: string;
  /** PRD-045: PROJ-BUG-12. */
  key: string;
  title: string;
  description: string;
  stepsToReproduce: string;
  expectedBehavior: string;
  actualBehavior: string;
  priority: Priority;
  status: BugReportStatus;
  resolution: BugResolution | null;
  duplicateOfId: string | null;
  duplicateOfKey: string | null;
  environment: string;
  projectId: string;
  testResultId: string | null;
  testCaseTitle: string | null;
  testRunId: string | null;
  testRunName: string | null;
  assigneeId: string | null;
  assigneeName: string | null;
  createdBy: string;
  reporterName: string | null;
  /** PRD-046: who last changed it. */
  updatedBy: string | null;
  updatedByName: string | null;
  createdAt: string;
  updatedAt: string;
  projectKey: string | null;
  /** PRD-034: the exploratory session it was found in. */
  exploratorySessionId: string | null;
  exploratorySessionKey: string | null;
  /** PRD-035: only fields that hold a value, in display order. */
  customFields: CustomFieldValues;
}

export interface CreateBugReportRequest {
  title: string;
  description?: string;
  stepsToReproduce?: string;
  expectedBehavior?: string;
  actualBehavior?: string;
  priority: Priority;
  environment?: string;
  testResultId?: string;
  testRunId?: string;
  assigneeId?: string;
  /** PRD-034: filed from this exploratory session. */
  exploratorySessionId?: string;
  customFields?: CustomFieldValues;
}

export interface UpdateBugReportRequest {
  title: string;
  description?: string;
  stepsToReproduce?: string;
  expectedBehavior?: string;
  actualBehavior?: string;
  priority: Priority;
  environment?: string;
  testResultId?: string;
  testRunId?: string;
  assigneeId?: string;
  customFields?: CustomFieldValues;
}

/** The bug list's filters, as they appear in the URL (PRD-045). assignee: user ids, 'none', 'me'. */
export interface BugReportQuery {
  q?: string;
  status?: BugReportStatus[];
  priority?: Priority[];
  assignee?: string[];
  testResultId?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export interface ChangeBugStatusRequest {
  status: BugReportStatus;
  reason: string;
  resolution?: BugResolution | null;
  duplicateOfId?: string | null;
}

/** One change to many bugs, all or nothing; only the fields given change. */
export interface BulkUpdateBugReportsRequest {
  ids: string[];
  assigneeId?: string;
  clearAssignee?: boolean;
  priority?: Priority;
  status?: BugReportStatus;
  resolution?: BugResolution | null;
  duplicateOfId?: string | null;
  reason?: string;
}
