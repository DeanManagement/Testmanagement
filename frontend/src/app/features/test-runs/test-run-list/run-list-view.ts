import { TestRun, TestResultStatus } from '../../../shared/models/test-run.model';

/** Columns a user can switch on (PRD-049); the rest are always shown. */
export const OPTIONAL_RUN_COLUMNS = ['plan', 'executor', 'started', 'ended'] as const;
export type OptionalRunColumn = (typeof OPTIONAL_RUN_COLUMNS)[number];

const STORAGE_KEY = 'tm-run-columns';
const SEGMENT_ORDER: TestResultStatus[] = ['PASSED', 'FAILED', 'BLOCKED', 'SKIPPED', 'PENDING'];

/** The table's columns in display order, with the chosen optional ones in their place. */
export function runColumns(optional: ReadonlySet<OptionalRunColumn>): string[] {
  return ['key', 'name', 'environment', ...OPTIONAL_RUN_COLUMNS.filter((c) => optional.has(c)), 'status', 'results', 'actions'];
}

/** The saved choice; storage can be missing or throw (private mode), which means none. */
export function readRunColumns(storage: Pick<Storage, 'getItem'> | undefined): Set<OptionalRunColumn> {
  try {
    const saved = JSON.parse(storage?.getItem(STORAGE_KEY) ?? '[]');
    return new Set((Array.isArray(saved) ? saved : []).filter(isOptionalColumn));
  } catch {
    return new Set();
  }
}

export function writeRunColumns(storage: Pick<Storage, 'setItem'> | undefined, columns: ReadonlySet<OptionalRunColumn>): void {
  try {
    storage?.setItem(STORAGE_KEY, JSON.stringify([...columns]));
  } catch {
    // Not saved: the choice still holds for this page.
  }
}

/** The run's results as bar segments in a fixed order, as percentages of the total; empty ones left out. */
export function resultSegments(run: TestRun): { status: TestResultStatus; count: number; percent: number }[] {
  const total = run.total ?? 0;
  if (total === 0) return [];
  const counts: Record<TestResultStatus, number> = {
    PASSED: run.passed ?? 0, FAILED: run.failed ?? 0, BLOCKED: run.blocked ?? 0,
    SKIPPED: run.skipped ?? 0, PENDING: run.pending ?? 0,
  };
  return SEGMENT_ORDER.filter((status) => counts[status] > 0)
    .map((status) => ({ status, count: counts[status], percent: (counts[status] * 100) / total }));
}

function isOptionalColumn(value: unknown): value is OptionalRunColumn {
  return OPTIONAL_RUN_COLUMNS.includes(value as OptionalRunColumn);
}
