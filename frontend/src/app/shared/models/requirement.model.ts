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
  /** TM18: the requirement this one refines; null at the top of the tree. */
  parentId: string | null;
  status: RequirementStatus;
  priority: Priority;
  customFields: CustomFieldValues;
  testCases: LinkedTestCase[];
  createdAt: string;
  updatedAt: string;
  /** TM19: the tracker it is synced from; null for a local requirement. Synced fields are read-only. */
  source: string | null;
  sourceUrl: string | null;
  lastSyncedAt: string | null;
  /** TM46: bugs linked to it by hand that are still open. */
  openBugs: { id: string; key: string; title: string; status: string }[];
}

/** TM19: requirement sync settings (Azure DevOps; `query` is WIQL). */
export interface RequirementSyncConfig {
  enabled: boolean;
  scheduled: boolean;
  query: string;
  externalIdPrefix: string | null;
  approvedStates: string;
  obsoleteStates: string;
  lastSyncedAt: string | null;
  lastError: string | null;
  lastErrorAt: string | null;
}

export interface SaveRequirementSyncConfigRequest {
  enabled: boolean;
  scheduled: boolean;
  query: string;
  externalIdPrefix: string | null;
  approvedStates: string;
  obsoleteStates: string;
}

export interface RequirementSyncStatus {
  enabled: boolean;
  lastSyncedAt: string | null;
  lastError: string | null;
}

export interface RequirementSyncResult {
  created: number;
  updated: number;
  obsoleted: number;
  unchanged: number;
  dryRun: boolean;
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
  parentId: string | null;
  status: RequirementStatus;
  priority: Priority;
  customFields?: CustomFieldValues;
}

export interface TraceabilityRow {
  requirementId: string;
  externalId: string;
  title: string;
  parentId: string | null;
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
