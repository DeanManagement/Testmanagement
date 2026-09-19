/** A test plan's release-gate thresholds (PRD-037); null means that criterion is not used. */
export interface ReleaseGate {
  /** Percent, 0-100. */
  minPassRate: number | null;
  maxBlockerBugs: number | null;
  /** Percent, 0-100. */
  minCoverage: number | null;
  maxFlaky: number | null;
}

export type ReadinessVerdict = 'GO' | 'NO_GO' | 'NO_CRITERIA';
export type CriterionOutcome = 'PASS' | 'FAIL' | 'NOT_APPLICABLE';
export type CriterionName = 'PASS_RATE' | 'BLOCKER_BUGS' | 'COVERAGE' | 'FLAKY_TESTS';

export interface ReadinessCriterion {
  name: CriterionName;
  /** Absent when the criterion does not apply, e.g. coverage without requirements. */
  actual?: number;
  threshold: number;
  outcome: CriterionOutcome;
}

export interface ReadinessCounts {
  considered: number;
  passed: number;
  failed: number;
  blocked: number;
  skipped: number;
  pending: number;
}

/** Computed on read; only configured criteria are listed. */
export interface Readiness {
  planId: string;
  planName: string;
  verdict: ReadinessVerdict;
  evaluatedAt: string;
  criteria: ReadinessCriterion[];
  counts: ReadinessCounts;
}
