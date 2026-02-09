export type TestRunStatus = 'PLANNED' | 'IN_PROGRESS' | 'COMPLETED' | 'ABORTED';
export type TestResultStatus = 'PENDING' | 'PASSED' | 'FAILED' | 'BLOCKED' | 'SKIPPED';

export interface StepResult {
  id: string;
  testStepId: string;
  action: string;
  expectedResult: string;
  orderIndex: number;
  status: TestResultStatus;
  actualResult: string;
  screenshotId: string | null;
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
}

export interface TestRun {
  id: string;
  name: string;
  environment: string;
  status: TestRunStatus;
  startTime: string;
  endTime: string;
  results: TestResult[];
  createdAt: string;
  updatedAt: string;
}

export interface CreateTestRunRequest {
  name: string;
  environment?: string;
  testCaseIds?: string[];
}

export interface UpdateTestRunRequest {
  name: string;
  environment?: string;
  status?: TestRunStatus;
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
