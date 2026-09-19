import { describe, expect, it } from 'vitest';
import { burnDownDates } from './burn-down-dates';

const point = (date: string) => ({ date, remainingMinutes: 0 });

describe('burnDownDates', () => {
  it('should cover the actual days and the ideal line up to the target date, once each, in order', () => {
    const dates = burnDownDates({
      days: [point('2026-09-01'), point('2026-09-02'), point('2026-09-03')],
      idealLine: [point('2026-09-02'), point('2026-09-03'), point('2026-09-04'), point('2026-09-05')],
      scopeStartsAt: '2026-09-02',
      historyAvailableFrom: null,
      hasEstimates: true,
    });

    expect(dates).toEqual(['2026-09-01', '2026-09-02', '2026-09-03', '2026-09-04', '2026-09-05']);
  });

  it('should be just the actual days without a target date', () => {
    const dates = burnDownDates({
      days: [point('2026-09-01'), point('2026-09-02')],
      idealLine: [],
      scopeStartsAt: '2026-09-01',
      historyAvailableFrom: null,
      hasEstimates: true,
    });

    expect(dates).toEqual(['2026-09-01', '2026-09-02']);
  });
});
