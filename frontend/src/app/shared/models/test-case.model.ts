import { CustomFieldValues } from './custom-field.model';

export interface TestStep {
  id: string;
  action: string;
  expectedResult: string;
  testData: string;
  orderIndex: number;
  imageId: string | null;
}

export interface TestStepRequest {
  action: string;
  expectedResult: string;
  testData?: string;
}

export type Priority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
/** With a project's review switch on (PRD-033), ACTIVE means approved. */
export type TestCaseStatus = 'DRAFT' | 'IN_REVIEW' | 'ACTIVE' | 'DEPRECATED';

export const ALL_TEST_CASE_STATUSES: TestCaseStatus[] = ['DRAFT', 'IN_REVIEW', 'ACTIVE', 'DEPRECATED'];

export interface TestCase {
  id: string;
  key: string;
  title: string;
  description: string;
  preconditions: string;
  priority: Priority;
  status: TestCaseStatus;
  labels: string[];
  steps: TestStep[];
  folderId: string | null;
  createdAt: string;
  updatedAt: string;
  createdBy?: string;
  updatedBy?: string;
  currentVersion: number;
  /** PRD-033: null when never approved, including ACTIVE cases from before review was on. */
  approvedBy: string | null;
  approvedAt: string | null;
  approvedVersion: number | null;
  /** PRD-035: only fields that hold a value, in display order. */
  customFields: CustomFieldValues;
  /** PRD-036: expected minutes for one execution; null when not estimated. */
  estimateMinutes: number | null;
  /** PRD-036: median measured duration of the last 5 executions; detail responses only. */
  medianActualMs?: number | null;
}

/** What the caller may do in a case's review; reason explains a refused approve. */
export interface ReviewCapabilities {
  reviewRequired: boolean;
  canSubmit: boolean;
  canApprove: boolean;
  reason: string | null;
}

export interface CreateTestCaseRequest {
  title: string;
  description?: string;
  preconditions?: string;
  priority?: Priority;
  status?: TestCaseStatus;
  labels?: string[];
  steps?: TestStepRequest[];
  folderId?: string;
  customFields?: CustomFieldValues;
  estimateMinutes?: number;
}

export interface UpdateTestCaseRequest {
  title: string;
  description?: string;
  preconditions?: string;
  priority?: Priority;
  status?: TestCaseStatus;
  labels?: string[];
  steps?: TestStepRequest[];
  customFields?: CustomFieldValues;
  /** Omitted leaves it alone; 0 clears it. */
  estimateMinutes?: number;
}

export interface BulkOperationResponse {
  affected: number;
  message: string;
}

export interface ImportError {
  row: number;
  message: string;
}

export interface ImportResult {
  imported: number;
  skipped: number;
  dryRun: boolean;
  errors: ImportError[];
  /** Rows imported with a change, e.g. ACTIVE as IN_REVIEW under review (PRD-033). */
  warnings: ImportError[];
  /** Gherkin only (PRD-040): @tm:-keyed scenarios that changed their case, and those that did not. */
  updated: number;
  unchanged: number;
}

/** One scenario read by the server, for the form's "Edit as Gherkin" (PRD-040 §3.7). */
export interface GherkinPreview {
  key: string | null;
  title: string;
  description: string | null;
  preconditions: string | null;
  priority: Priority | null;
  labels: string[];
  steps: TestStepRequest[];
  parameterSets: { name: string; values: Record<string, string> }[];
  /** What an import would refuse. */
  problems: string[];
  /** What an import would change or drop. */
  warnings: string[];
}

export interface TestCaseQuery {
  q?: string;
  status?: TestCaseStatus[];
  priority?: Priority[];
  label?: string[];
  folderId?: string | null;
  /** With folderId: also list cases in its subfolders. */
  includeSubfolders?: boolean;
  rootOnly?: boolean;
  updatedAfter?: string;
  /** PRD-035: raw `cf.<name>` filter parameters, passed through to the API as they are. */
  customFieldParams?: Record<string, string[]>;
  page?: number;
  size?: number;
  sort?: string;
}
