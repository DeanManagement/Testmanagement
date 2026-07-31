import { TestPlanStatus } from './test-plan.model';
import { BugReportStatus } from './bug-report.model';

/**
 * "My queue" payload as returned by `GET /api/me/queue`. Each bucket is
 * capped server-side; the frontend just renders what it gets.
 */
export interface MyQueueResponse {
  dueTestPlans: DueTestPlanItem[];
  inProgressRuns: InProgressRunItem[];
  staleBugReports: StaleBugReportItem[];
  oldDraftTestCases: OldDraftTestCaseItem[];
}

export interface DueTestPlanItem {
  id: string;
  name: string;
  projectId: string;
  projectKey: string;
  projectName: string;
  status: TestPlanStatus;
  /** ISO date string `YYYY-MM-DD`. */
  targetDate: string;
}

export interface InProgressRunItem {
  id: string;
  key: string;
  name: string;
  projectId: string;
  projectKey: string;
  projectName: string;
  updatedAt: string;
}

export interface StaleBugReportItem {
  id: string;
  title: string;
  projectId: string;
  projectKey: string;
  projectName: string;
  status: BugReportStatus;
  updatedAt: string;
}

export interface OldDraftTestCaseItem {
  id: string;
  key: string;
  title: string;
  projectId: string;
  projectKey: string;
  projectName: string;
  updatedAt: string;
}
