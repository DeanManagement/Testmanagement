import { Priority, TestCaseStatus } from './test-case.model';

/** One historical state of a test case (PRD-011). */
export interface TestCaseVersion {
  id: string | null;
  versionNumber: number;
  versionAt: string;
  title: string;
  description: string | null;
  preconditions: string | null;
  priority: Priority;
  status: TestCaseStatus;
  labels: string[];
  steps: TestCaseVersionStep[];
  createdBy: string | null;
}

export interface TestCaseVersionStep {
  orderIndex: number;
  action: string;
  expectedResult: string | null;
  testData: string | null;
}

export interface TestCaseVersionSummary {
  id: string | null;
  versionNumber: number;
  versionAt: string;
  title: string;
  createdBy: string | null;
  /** The live state, which has no snapshot row of its own. */
  current: boolean;
}
