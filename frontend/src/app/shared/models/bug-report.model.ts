export type BugReportStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED' | 'WONTFIX';

export type Priority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export interface BugReport {
  id: string;
  title: string;
  description: string;
  stepsToReproduce: string;
  expectedBehavior: string;
  actualBehavior: string;
  priority: Priority;
  status: BugReportStatus;
  environment: string;
  projectId: string;
  testResultId: string | null;
  testCaseTitle: string | null;
  testRunId: string | null;
  testRunName: string | null;
  assigneeId: string | null;
  assigneeName: string | null;
  createdBy: string;
  reporterName: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateBugReportRequest {
  title: string;
  description?: string;
  stepsToReproduce?: string;
  expectedBehavior?: string;
  actualBehavior?: string;
  priority: Priority;
  environment?: string;
  testResultId?: string;
  testRunId?: string;
  assigneeId?: string;
}

export interface UpdateBugReportRequest {
  title: string;
  description?: string;
  stepsToReproduce?: string;
  expectedBehavior?: string;
  actualBehavior?: string;
  priority: Priority;
  status: BugReportStatus;
  environment?: string;
  testResultId?: string;
  testRunId?: string;
  assigneeId?: string;
}
