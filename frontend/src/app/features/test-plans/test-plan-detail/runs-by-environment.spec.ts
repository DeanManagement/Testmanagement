import { describe, expect, it } from 'vitest';
import { TestPlanRunSummary } from '../../../shared/models/test-plan.model';
import { runsByEnvironment } from './runs-by-environment';

function run(environment: string | null, total: number, passed: number, failed = 0): TestPlanRunSummary {
  return { id: crypto.randomUUID(), name: 'r', environment: environment as string, status: 'COMPLETED', total, passed, failed, endTime: null };
}

describe('runsByEnvironment', () => {
  it('should add up runs of the same environment and compute the pass rate', () => {
    const [staging] = runsByEnvironment([run('staging', 10, 9, 1), run('staging', 10, 6, 4)]);

    expect(staging).toEqual({ environment: 'staging', runs: 2, total: 20, passed: 15, failed: 5, passRate: 75 });
  });

  it('should keep environments apart and put runs without one last', () => {
    const rollups = runsByEnvironment([run(null, 1, 1), run('prod', 2, 2), run('staging', 3, 3)]);

    expect(rollups.map((r) => r.environment)).toEqual(['prod', 'staging', null]);
  });

  it('should report no pass rate when nothing was recorded', () => {
    expect(runsByEnvironment([run('prod', 0, 0)])[0].passRate).toBeNull();
  });
});
