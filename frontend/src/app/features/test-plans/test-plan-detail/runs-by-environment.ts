import { TestPlanRunSummary } from '../../../shared/models/test-plan.model';

/** One environment's share of a plan (PRD-032); a null environment groups runs without one. */
export interface EnvironmentRollup {
  environment: string | null;
  runs: number;
  total: number;
  passed: number;
  failed: number;
  /** Percentage with one decimal, or null when no results were recorded. */
  passRate: number | null;
}

/** Groups the plan's runs by environment, in first-seen order with "unspecified" last. */
export function runsByEnvironment(runs: readonly TestPlanRunSummary[]): EnvironmentRollup[] {
  const groups = new Map<string | null, EnvironmentRollup>();
  for (const run of runs) {
    const key = run.environment || null;
    const group = groups.get(key) ?? { environment: key, runs: 0, total: 0, passed: 0, failed: 0, passRate: null };
    group.runs += 1;
    group.total += run.total;
    group.passed += run.passed;
    group.failed += run.failed;
    groups.set(key, group);
  }
  const rollups = [...groups.values()].map((group) => ({
    ...group,
    passRate: group.total > 0 ? Math.round((group.passed * 1000) / group.total) / 10 : null,
  }));
  return [...rollups.filter((r) => r.environment !== null), ...rollups.filter((r) => r.environment === null)];
}
