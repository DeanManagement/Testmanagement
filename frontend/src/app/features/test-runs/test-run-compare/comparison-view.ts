import { ComparisonCategory, ComparisonCounts, ComparisonRow, COMPARISON_CATEGORIES, RunComparison } from '../../../shared/models/run-comparison.model';

export interface CategoryGroup {
  category: ComparisonCategory;
  count: number;
  rows: ComparisonRow[];
}

const COUNT_OF: Record<ComparisonCategory, keyof ComparisonCounts> = {
  NEWLY_FAILING: 'newlyFailing',
  FIXED: 'fixed',
  STILL_FAILING: 'stillFailing',
  ADDED: 'added',
  REMOVED: 'removed',
  OTHER_CHANGE: 'otherChange',
  UNCHANGED: 'unchanged',
};

/**
 * One group per category that has anything, in display order. Unchanged is counted even when its
 * rows were not requested, so its group can say how many there are without listing them.
 */
export function groupByCategory(comparison: RunComparison): CategoryGroup[] {
  return COMPARISON_CATEGORIES
    .map((category) => ({
      category,
      count: comparison.counts[COUNT_OF[category]],
      rows: comparison.rows.filter((row) => row.category === category),
    }))
    .filter((group) => group.count > 0);
}

/** Nothing moved: only unchanged results, or none at all. */
export function hasDifferences(counts: ComparisonCounts): boolean {
  return COMPARISON_CATEGORIES.some((category) => category !== 'UNCHANGED' && counts[COUNT_OF[category]] > 0);
}

/** The base happened after the head, so "newly failing" reads backwards; the page offers a swap. */
export function isBaseNewer(comparison: RunComparison): boolean {
  return comparison.base.happenedAt > comparison.head.happenedAt;
}
