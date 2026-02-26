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
}

export interface TestResult {
  id: string;
  testCaseId: string;
  testCaseTitle: string;
  status: TestResultStatus;
  comment: string;
  defectLink: string;
  stepResults: StepResult[];
  createdAt: string;
  updatedAt: string;
  createdBy?: string;
  updatedBy?: string;
}

export interface TestRun {
  id: string;
  name: string;
  environment: string;
  status: TestRunStatus;
  startTime: string;
  endTime: string;
  executorName: string | null;
  completedByName: string | null;
  reopenReason: string | null;
  testPlanId: string | null;
  testPlanName: string | null;
  allureReportId: string | null;
  results: TestResult[];
  createdAt: string;
  updatedAt: string;
  createdBy?: string;
  updatedBy?: string;
}

export interface CreateTestRunRequest {
  name: string;
  environment?: string;
  testCaseIds?: string[];
  testPlanId?: string;
}

export interface UpdateTestRunRequest {
  name: string;
  environment?: string;
  status?: TestRunStatus;
  reopenReason?: string;
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
}
