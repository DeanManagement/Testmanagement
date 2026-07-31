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
export type TestCaseStatus = 'DRAFT' | 'ACTIVE' | 'DEPRECATED';

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
}

export interface TestCaseQuery {
  q?: string;
  status?: TestCaseStatus[];
  priority?: Priority[];
  label?: string[];
  folderId?: string | null;
  rootOnly?: boolean;
  updatedAfter?: string;
  page?: number;
  size?: number;
  sort?: string;
}
