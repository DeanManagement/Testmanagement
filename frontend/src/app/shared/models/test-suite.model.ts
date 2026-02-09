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
