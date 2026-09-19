import { describe, expect, it } from 'vitest';
import { priorityBars, trendDataset } from './dashboard-metrics';

/** PRD-049: the dashboard's charts read in rank order and never invent a 0 %. */
describe('dashboard metrics', () => {
  it('lists priorities from critical to low, whatever order the counts came in, skipping empty ones', () => {
    expect(priorityBars({ LOW: 4, CRITICAL: 1, MEDIUM: 0, HIGH: 2 }))
      .toEqual([{ priority: 'CRITICAL', count: 1 }, { priority: 'HIGH', count: 2 }, { priority: 'LOW', count: 4 }]);
  });

  it('draws a run that executed nothing as a gap, on a straight line', () => {
    const dataset = trendDataset([
      { testRunId: 'a', name: 'A', completedAt: '', passRate: 80 },
      { testRunId: 'b', name: 'B', completedAt: '', passRate: null },
      { testRunId: 'c', name: 'C', completedAt: '', passRate: 90 },
    ], 'Pass rate');

    expect(dataset.data).toEqual([80, null, 90]);
    expect(dataset.tension).toBe(0);
    expect(dataset.spanGaps).toBe(false);
  });
});
