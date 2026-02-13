export interface ProjectDashboard {
  totals: DashboardTotals;
  testCasesByStatus: Record<string, number>;
  testCasesByPriority: Record<string, number>;
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
