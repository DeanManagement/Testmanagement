export interface TestCaseSummary {
  id: string;
  title: string;
}

export interface TestSuite {
  id: string;
  name: string;
  description: string;
  testCases: TestCaseSummary[];
  createdAt: string;
  updatedAt: string;
  createdBy?: string;
  updatedBy?: string;
}

export interface CreateTestSuiteRequest {
  name: string;
  description?: string;
  testCaseIds?: string[];
}

export interface UpdateTestSuiteRequest {
  name: string;
  description?: string;
  testCaseIds?: string[];
}

export interface TestCaseLatestResult {
  testCaseId: string;
  testCaseTitle: string;
  status: string | null;
  testRunId: string | null;
  testRunName: string | null;
  updatedAt: string | null;
}

export interface TestSuiteReport {
  id: string;
  name: string;
  description: string;
  total: number;
  passed: number;
  failed: number;
  blocked: number;
  skipped: number;
  untested: number;
  passRate: number;
  results: TestCaseLatestResult[];
}
