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
  /** PRD-033: cases in review that I may approve. */
  awaitingReview: ReviewTestCaseItem[];
  /** TM23: pending results of open runs assigned to me. */
  assignedResults: AssignedResultItem[];
}

export interface AssignedResultItem {
  id: string;
  runId: string;
  runKey: string;
  testCaseKey: string;
  testCaseTitle: string;
  projectId: string;
  projectKey: string;
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
  /** TM47: the project's own status name; null for a bug without one. */
  statusName?: string | null;
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

export interface ReviewTestCaseItem {
  id: string;
  key: string;
  title: string;
  projectId: string;
  projectKey: string;
  projectName: string;
  updatedAt: string;
}
