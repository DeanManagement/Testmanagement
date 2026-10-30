import { CustomFieldValues } from './custom-field.model';

/** PRD-045: NEW is reported but not yet triaged; OPEN is confirmed. */
export type BugReportStatus = 'NEW' | 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED';

export const ALL_BUG_STATUSES: BugReportStatus[] = ['NEW', 'OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'];

/** Still needs work; mirrors BugReportStatus.OPEN_STATUSES on the server. */
export const OPEN_BUG_STATUSES: BugReportStatus[] = ['NEW', 'OPEN', 'IN_PROGRESS'];

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
  /** PRD-047: the case of the found-in result, and the found-in step (numbered from 1). */
  testCaseId: string | null;
  testCaseKey: string | null;
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
  stepResultId: string | null;
  stepNumber: number | null;
  /** PRD-047: where else it showed up; the found-in result is testResultId. */
  links: BugReportLink[];
  /** PRD-035: only fields that hold a value, in display order. */
  customFields: CustomFieldValues;
  /** TM49 */
  labels: string[];
  foundInPlanId: string | null;
  foundInPlanName: string | null;
  fixedInPlanId: string | null;
  fixedInPlanName: string | null;
  /** TM47: the project's own status; status stays its category. */
  customStatusId: string | null;
  customStatusName: string | null;
  customStatusColor: string | null;
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
  /** PRD-047: the failing step of testResultId. */
  stepResultId?: string;
  labels?: string[];
  foundInPlanId?: string;
  fixedInPlanId?: string;
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
  /** TM49: omitted keeps the labels; an empty list clears them. Absent plans are cleared. */
  labels?: string[];
  foundInPlanId?: string;
  fixedInPlanId?: string;
}

/** The bug list's filters, as they appear in the URL (PRD-045). assignee: user ids, 'none', 'me'. */
export interface BugReportQuery {
  q?: string;
  status?: BugReportStatus[];
  priority?: Priority[];
  assignee?: string[];
  testResultId?: string;
  /** TM48 */
  environmentId?: string;
  reporter?: string[];
  testRunId?: string;
  /** ISO days, inclusive. */
  createdFrom?: string;
  createdTo?: string;
  label?: string[];
  /** TM47: the project's own statuses; status filters by category. */
  statusId?: string[];
  page?: number;
  size?: number;
  sort?: string;
}

export interface ChangeBugStatusRequest {
  status: BugReportStatus;
  /** TM47: the project's own status; decides the category on the server. */
  statusId?: string | null;
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
  /** TM47 */
  statusId?: string | null;
}

/** A further occurrence of a bug (PRD-047). */
export interface BugReportLink {
  id: string;
  testRunId: string;
  testRunKey: string;
  testRunName: string;
  testResultId: string;
  testCaseId: string;
  testCaseKey: string;
  stepResultId: string | null;
  stepNumber: number | null;
  createdAt: string;
}

/** The dashboard's defect figures (PRD-047). Every status and priority is present. */
export interface DefectDashboard {
  open: number;
  byStatus: Record<BugReportStatus, number>;
  openByPriority: Record<Priority, number>;
  trend: { weekStart: string; created: number; resolved: number }[];
}

/** TM46: how two bug reports relate. BLOCKED_BY exists only in the UI: it is the other bug's BLOCKS. */
export type BugRelationType = 'BLOCKS' | 'RELATES_TO';

export interface BugLinkedItem {
  id: string;
  /** A test case's key or a requirement's external id. */
  key: string;
  title: string;
}

export interface BugRelation {
  id: string;
  type: BugRelationType;
  /** False when the other bug is the subject, e.g. it blocks this one. */
  outgoing: boolean;
  bugId: string;
  bugKey: string;
  bugTitle: string;
  bugStatus: BugReportStatus;
}

export interface BugReportRelations {
  testCases: BugLinkedItem[];
  requirements: BugLinkedItem[];
  relations: BugRelation[];
}

/** TM47: a project's own bug status, counting as one of the fixed categories. */
export interface BugStatus {
  id: string;
  name: string;
  color: string;
  category: BugReportStatus;
  position: number;
  archived: boolean;
  /** Active statuses a bug in this one may move to. */
  allowedTargetIds: string[];
}

export interface BugStatusTransition {
  fromStatusId: string;
  toStatusId: string;
}

/** Board and menu order: by category, then the project's own order within it. */
export function sortBugStatuses(statuses: BugStatus[]): BugStatus[] {
  return [...statuses].sort((a, b) => ALL_BUG_STATUSES.indexOf(a.category) - ALL_BUG_STATUSES.indexOf(b.category)
    || a.position - b.position || a.name.localeCompare(b.name));
}

/** The status a bug is in: its own, or for a bug without one, its category's first. */
export function statusOf(bug: BugReport, statuses: BugStatus[]): BugStatus | undefined {
  return statuses.find((s) => s.id === bug.customStatusId) ?? sortBugStatuses(statuses).find((s) => s.category === bug.status);
}
