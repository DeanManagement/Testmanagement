import { TestResultStatus, TestRunStatus } from './test-run.model';

/** Categories in display order: what a reader looks for first comes first (PRD-038). */
export const COMPARISON_CATEGORIES = [
  'NEWLY_FAILING', 'FIXED', 'STILL_FAILING', 'ADDED', 'REMOVED', 'OTHER_CHANGE', 'UNCHANGED',
] as const;
export type ComparisonCategory = typeof COMPARISON_CATEGORIES[number];

export interface ComparedRun {
  id: string;
  key: string;
  name: string;
  environment?: string;
  status: TestRunStatus;
  endTime?: string;
  /** End, else start, else creation time. */
  happenedAt: string;
}

export interface ComparisonCounts {
  newlyFailing: number;
  fixed: number;
  stillFailing: number;
  added: number;
  removed: number;
  otherChange: number;
  unchanged: number;
}

export interface ComparisonRow {
  category: ComparisonCategory;
  testCaseId: string;
  testCaseKey: string;
  title: string;
  parameterSetName?: string;
  /** Absent for ADDED. */
  baseStatus?: TestResultStatus;
  /** Absent for REMOVED. */
  headStatus?: TestResultStatus;
  baseResultId?: string;
  headResultId?: string;
  /** The two results ran different wording of the case. */
  versionChanged: boolean;
  /** Results beyond one per run collapsed into this row. */
  duplicates: number;
}

export interface RunComparison {
  base: ComparedRun;
  head: ComparedRun;
  baseAutoSelected: boolean;
  counts: ComparisonCounts;
  rows: ComparisonRow[];
}
