import { CustomFieldValues } from './custom-field.model';
import { EffortSummary } from './effort.model';

export type TestRunStatus = 'PLANNED' | 'IN_PROGRESS' | 'COMPLETED' | 'ABORTED';
export type TestResultStatus = 'PENDING' | 'PASSED' | 'FAILED' | 'BLOCKED' | 'SKIPPED';

export interface StepResult {
  id: string;
  testStepId: string;
  action: string;
  expectedResult: string;
  testData: string;
  orderIndex: number;
  status: TestResultStatus;
  actualResult: string;
  screenshotId: string | null;
  stepImageId: string | null;
  /** PRD-030: the shared step this step came from; consecutive steps with one title are one block. */
  sharedStepTitle?: string | null;
}

export interface TestResult {
  id: string;
  testCaseId: string;
  /** PRD-048: the case's key, its live texts and current version. */
  testCaseKey: string;
  testCaseTitle: string;
  testCasePreconditions: string | null;
  testCaseDescription: string | null;
  testCaseVersion: number | null;
  status: TestResultStatus;
  comment: string;
  defectLink: string | null;
  /** Version of the test case executed (PRD-011); null for results predating versioning. */
  executedVersion: number | null;
  /** Parameter set executed (PRD-015); null for an ordinary case. */
  parameterSetName: string | null;
  stepResults: StepResult[];
  /** PRD-036: when it left PENDING, and the measured effort; null when unknown. */
  executedAt: string | null;
  /** PRD-048: who executed it; the name is null for a deleted user. */
  executedBy: string | null;
  executedByName: string | null;
  durationMs: number | null;
  /** PRD-036: the case's live estimate. */
  estimateMinutes: number | null;
  createdAt: string;
  updatedAt: string;
  createdBy?: string;
  updatedBy?: string;
}

export interface TestRun {
  id: string;
  key: string;
  name: string;
  environment: string;
  status: TestRunStatus;
  startTime: string;
  endTime: string;
  executorName: string | null;
  completedByName: string | null;
  reopenReason: string | null;
  /** Why the run was aborted; set only while it is ABORTED. */
  abortReason?: string | null;
  testPlanId: string | null;
  testPlanName: string | null;
  allureReportId: string | null;
  projectId: string | null;
  projectKey: string | null;
  /** Present on detail responses only; list endpoints return counts instead. */
  results?: TestResult[];
  total?: number;
  passed?: number;
  failed?: number;
  blocked?: number;
  skipped?: number;
  pending?: number;
  createdAt: string;
  updatedAt: string;
  createdBy?: string;
  updatedBy?: string;
  /** PRD-035: on detail responses; only fields that hold a value, in display order. */
  customFields?: CustomFieldValues;
  /** PRD-036: on detail responses. */
  effort?: EffortSummary;
}

export interface TestRunQuery {
  q?: string;
  status?: TestRunStatus[];
  testPlanId?: string;
  executorId?: string;
  environmentId?: string;
  startedAfter?: string;
  /** PRD-035: raw `cf.<name>` filter parameters, passed through to the API as they are. */
  customFieldParams?: Record<string, string[]>;
  page?: number;
  size?: number;
  sort?: string;
}

export interface CreateTestRunRequest {
  name: string;
  environment?: string;
  testCaseIds?: string[];
  testPlanId?: string;
  executorId?: string;
  /** Only for createAcrossEnvironments: one run per id (PRD-032). */
  environmentIds?: string[];
  customFields?: CustomFieldValues;
}

/** Server-side limit on environmentIds in one request. */
export const MAX_ENVIRONMENTS_PER_REQUEST = 20;

export interface UpdateTestRunRequest {
  name: string;
  environment?: string;
  status?: TestRunStatus;
  reopenReason?: string;
  /** Required when status is ABORTED. */
  abortReason?: string;
}

export interface CompletionInfo {
  total: number;
  passed: number;
  failed: number;
  blocked: number;
  skipped: number;
  pending: number;
  worstStatus: string;
}

export interface CreateTestResultRequest {
  testCaseId: string;
  status: TestResultStatus;
  comment?: string;
  defectLink?: string;
}

export interface UpdateTestResultRequest {
  status: TestResultStatus;
  comment?: string;
  defectLink?: string;
  /** PRD-036: omitted leaves the recorded duration alone. */
  durationMs?: number;
  /** PRD-048: with PASSED or SKIPPED, the steps still pending take the same status. */
  cascadeSteps?: boolean;
}

export interface UpdateStepResultRequest {
  status: TestResultStatus;
  actualResult?: string;
}

export interface TestRunReport {
  id: string;
  name: string;
  environment: string;
  status: TestRunStatus;
  startTime: string;
  endTime: string;
  total: number;
  passed: number;
  failed: number;
  blocked: number;
  skipped: number;
  pending: number;
  passRate: number;
  results: TestResult[];
  /** PRD-033: results executed against wording that was never approved; empty without review. */
  unapprovedResultIds: string[];
  /** PRD-036 */
  effort: EffortSummary;
  /** PRD-048: the plan the run belongs to. */
  testPlanId: string | null;
  testPlanName: string | null;
}
