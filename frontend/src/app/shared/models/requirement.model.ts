import { CustomFieldValues } from './custom-field.model';
import { Priority } from './test-case.model';

/** Where a requirement is in its life (TM18). */
export type RequirementStatus = 'DRAFT' | 'APPROVED' | 'OBSOLETE';
export const REQUIREMENT_STATUSES: RequirementStatus[] = ['DRAFT', 'APPROVED', 'OBSOLETE'];

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
  status: RequirementStatus;
  priority: Priority;
  customFields: CustomFieldValues;
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
  status: RequirementStatus;
  priority: Priority;
  customFields?: CustomFieldValues;
}

export interface TraceabilityRow {
  requirementId: string;
  externalId: string;
  title: string;
  status: RequirementStatus;
  priority: Priority;
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
