export interface ProjectDashboard {
  totals: DashboardTotals;
  testCasesByStatus: Record<string, number>;
  testCasesByPriority: Record<string, number>;
  latestResultsByStatus: Record<string, number>;
  overallPassRate: number;
  recentTestRuns: RecentTestRun[];
  passRateTrend: PassRateTrendEntry[];
}

export interface DashboardTotals {
  totalTestCases: number;
  totalTestSuites: number;
  totalTestRuns: number;
  completedTestRuns: number;
}

export interface RecentTestRun {
  id: string;
  name: string;
  environment: string;
  status: string;
  startTime: string | null;
  endTime: string | null;
  total: number;
  passed: number;
  failed: number;
}

export interface PassRateTrendEntry {
  testRunId: string;
  name: string;
  completedAt: string;
  passRate: number;
}

/** A test case that keeps changing outcome across recent runs (PRD-016). */
export interface FlakyTest {
  testCaseId: string;
  testCaseKey: string;
  title: string;
  /** Proportion of consecutive pass/fail pairs that changed outcome, in [0,1]. */
  flakyScore: number;
  failRate: number;
  runsConsidered: number;
  flaky: boolean;
}
