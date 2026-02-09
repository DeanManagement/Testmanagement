export interface TestStep {
  id: string;
  action: string;
  expectedResult: string;
  orderIndex: number;
}

export interface TestStepRequest {
  action: string;
  expectedResult: string;
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
  createdAt: string;
  updatedAt: string;
}

export interface CreateTestCaseRequest {
  title: string;
  description?: string;
  preconditions?: string;
  priority?: Priority;
  status?: TestCaseStatus;
  labels?: string[];
  steps?: TestStepRequest[];
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
