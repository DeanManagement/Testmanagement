export type TestPlanStatus = 'OPEN' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';

export interface TestPlan {
  id: string;
  name: string;
  description: string;
  status: TestPlanStatus;
  targetDate: string | null;
  testRunCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateTestPlanRequest {
  name: string;
  description?: string;
  targetDate?: string;
}

export interface UpdateTestPlanRequest {
  name: string;
  description?: string;
  status?: TestPlanStatus;
  targetDate?: string;
}

export interface TestPlanRunSummary {
  id: string;
  name: string;
  environment: string;
  status: string;
  total: number;
  passed: number;
  failed: number;
  endTime: string | null;
}

export interface TestPlanSummary {
  id: string;
  name: string;
  status: TestPlanStatus;
  targetDate: string | null;
  totalRuns: number;
  completedRuns: number;
  totalResults: number;
  passed: number;
  failed: number;
  blocked: number;
  skipped: number;
  pending: number;
  passRate: number;
  runs: TestPlanRunSummary[];
}
