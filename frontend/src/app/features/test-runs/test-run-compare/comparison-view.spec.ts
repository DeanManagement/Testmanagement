import { describe, expect, it } from 'vitest';
import { ComparisonCounts, ComparisonRow, RunComparison } from '../../../shared/models/run-comparison.model';
import { groupByCategory, hasDifferences, isBaseNewer } from './comparison-view';

const NONE: ComparisonCounts = { newlyFailing: 0, fixed: 0, stillFailing: 0, added: 0, removed: 0, otherChange: 0, unchanged: 0 };

function row(category: ComparisonRow['category'], key: string): ComparisonRow {
  return { category, testCaseId: key, testCaseKey: key, title: key, versionChanged: false, duplicates: 0 };
}

function comparison(counts: Partial<ComparisonCounts>, rows: ComparisonRow[], baseAt = '2026-09-18T10:00:00Z',
                    headAt = '2026-09-19T10:00:00Z'): RunComparison {
  const run = (id: string, happenedAt: string) => ({ id, key: id, name: 'nightly', status: 'COMPLETED' as const, happenedAt });
  return { base: run('b', baseAt), head: run('h', headAt), baseAutoSelected: true, counts: { ...NONE, ...counts }, rows };
}

describe('comparison view', () => {
  describe('groupByCategory', () => {
    it('should keep display order and skip empty categories', () => {
      const groups = groupByCategory(comparison({ fixed: 1, newlyFailing: 2 },
        [row('FIXED', 'P-3'), row('NEWLY_FAILING', 'P-1'), row('NEWLY_FAILING', 'P-2')]));

      expect(groups.map((g) => [g.category, g.count, g.rows.length])).toEqual([
        ['NEWLY_FAILING', 2, 2],
        ['FIXED', 1, 1],
      ]);
    });

    it('should keep the unchanged count even when its rows were not requested', () => {
      const groups = groupByCategory(comparison({ unchanged: 240 }, []));

      expect(groups).toEqual([{ category: 'UNCHANGED', count: 240, rows: [] }]);
    });
  });

  describe('hasDifferences', () => {
    it('should ignore unchanged results', () => {
      expect(hasDifferences({ ...NONE, unchanged: 12 })).toBe(false);
    });

    it('should count any other category', () => {
      expect(hasDifferences({ ...NONE, otherChange: 1 })).toBe(true);
    });
  });

  describe('isBaseNewer', () => {
    it('should be false in the usual order and true when the base happened later', () => {
      expect(isBaseNewer(comparison({}, []))).toBe(false);
      expect(isBaseNewer(comparison({}, [], '2026-09-20T10:00:00Z'))).toBe(true);
    });
  });
});
