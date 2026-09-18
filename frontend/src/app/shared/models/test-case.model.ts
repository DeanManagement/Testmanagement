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
}

export interface UpdateTestCaseRequest {
  title: string;
  description?: string;
  preconditions?: string;
  priority?: Priority;
  status?: TestCaseStatus;
  labels?: string[];
  steps?: TestStepRequest[];
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
  page?: number;
  size?: number;
  sort?: string;
}
