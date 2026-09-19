import { ReleaseGate } from './readiness.model';
import { EffortSummary } from './effort.model';

export type TestPlanStatus = 'OPEN' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';

export interface TestPlan {
  id: string;
  name: string;
  description: string;
  status: TestPlanStatus;
  targetDate: string | null;
  testRunCount: number;
  assigneeId: string | null;
  assigneeName: string | null;
  createdAt: string;
  updatedAt: string;
  createdBy?: string;
  updatedBy?: string;
  /** PRD-037: always present; all-null thresholds mean no criteria. */
  gate: ReleaseGate;
}

export interface CreateTestPlanRequest {
  name: string;
  description?: string;
  targetDate?: string;
  assigneeId?: string;
  gate?: ReleaseGate;
}

export interface UpdateTestPlanRequest {
  name: string;
  description?: string;
  status?: TestPlanStatus;
  targetDate?: string;
  assigneeId?: string;
  /** Omitted leaves the gate as it is; a null threshold switches that criterion off. */
  gate?: ReleaseGate;
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
  /** PRD-034: exploration for this plan, kept apart from run counts and pass rate. */
  sessions: TestPlanSessions;
  /** PRD-036: over every run except aborted ones. */
  effort: EffortSummary;
}

export interface TestPlanSessions {
  total: number;
  completed: number;
  totalMinutes: number;
  items: TestPlanSessionItem[];
}

export interface TestPlanSessionItem {
  id: string;
  key: string;
  charter: string;
  status: string;
  testerName: string | null;
  startedAt: string | null;
  endedAt: string | null;
  minutes: number;
}
