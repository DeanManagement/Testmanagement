/** Coverage of a requirement, worst-cell-first (PRD-014). */
export type CoverageStatus =
  | 'PASSED'
  | 'FAILED'
  | 'BLOCKED'
  | 'SKIPPED'
  | 'UNTESTED'
  | 'UNCOVERED';

export interface Requirement {
  id: string;
  externalId: string;
  title: string;
  description: string | null;
  testCases: LinkedTestCase[];
  createdAt: string;
  updatedAt: string;
}

export interface LinkedTestCase {
  id: string;
  key: string;
  title: string;
}

export interface SaveRequirementRequest {
  externalId: string;
  title: string;
  description?: string;
}

export interface TraceabilityRow {
  requirementId: string;
  externalId: string;
  title: string;
  cells: TraceabilityCell[];
  coverage: CoverageStatus;
}

export interface TraceabilityCell {
  testCaseId: string;
  testCaseKey: string;
  testCaseTitle: string;
  status: CoverageStatus;
}

/**
 * Coverage counts requirements whose tests have *passed*, not those with a test attached —
 * "a test exists" and "a test proves it works" are different claims.
 */
export interface CoverageSummary {
  totalRequirements: number;
  uncovered: number;
  untested: number;
  failing: number;
  passing: number;
  coveragePercent: number;
}
