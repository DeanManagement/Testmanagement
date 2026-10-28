import { Sort } from '@angular/material/sort';
import { sortRows } from '../../shared/utils/sort-rows';
import { CoverageStatus, Requirement, TraceabilityRow } from '../../shared/models/requirement.model';

export type RequirementSortColumn = 'externalId' | 'title' | 'coverage';

export interface RequirementViewOptions {
  query: string;
  coverage: CoverageStatus | '';
  sort: Sort;
}

/** Worst first, matching how the backend rolls a row up, so "sort by coverage" surfaces gaps. */
const COVERAGE_RANK: Record<CoverageStatus, number> = {
  FAILED: 0,
  BLOCKED: 1,
  UNTESTED: 2,
  SKIPPED: 3,
  UNCOVERED: 4,
  PASSED: 5,
};

/**
 * Narrows and orders the requirements page on the client (TM17). The page already loads every
 * requirement and the whole matrix, so a server round-trip per keystroke would buy nothing.
 */
export function viewRequirements(
  requirements: Requirement[],
  rowsById: Map<string, TraceabilityRow>,
  options: RequirementViewOptions,
): Requirement[] {
  const needle = options.query.trim().toLowerCase();
  const matching = requirements.filter((requirement) => {
    if (needle
      && !requirement.externalId.toLowerCase().includes(needle)
      && !requirement.title.toLowerCase().includes(needle)) {
      return false;
    }
    return !options.coverage || rowsById.get(requirement.id)?.coverage === options.coverage;
  });
  return sortRows(matching, options.sort, (requirement, column) => {
    if (column === 'coverage') {
      const coverage = rowsById.get(requirement.id)?.coverage;
      return coverage ? COVERAGE_RANK[coverage] : null;
    }
    return column === 'title' ? requirement.title : requirement.externalId;
  });
}
