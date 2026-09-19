import { PassRateTrendEntry } from '../../../shared/models/dashboard.model';

/** Priorities from most to least urgent, the order a reader scans them in (PRD-049). */
export const PRIORITY_RANK = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'] as const;

/** The priorities that have cases, in rank order, with their counts. */
export function priorityBars(counts: Record<string, number>): { priority: string; count: number }[] {
  return PRIORITY_RANK.filter((priority) => (counts[priority] ?? 0) > 0)
    .map((priority) => ({ priority, count: counts[priority] }));
}

/**
 * The trend's points (PRD-049): a run that executed nothing is a gap, never a 0 % dip, and the line
 * is straight between points, since a smoothed curve invents values between runs.
 */
export function trendDataset(trend: readonly PassRateTrendEntry[], label: string) {
  return {
    label,
    data: trend.map((entry) => entry.passRate),
    borderColor: '#4caf50',
    backgroundColor: 'rgba(76, 175, 80, 0.1)',
    fill: true,
    tension: 0,
    spanGaps: false,
  };
}
