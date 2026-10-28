import { Sort } from '@angular/material/sort';
import { sortRows } from '../../shared/utils/sort-rows';
import { CoverageStatus, Requirement, RequirementStatus, TraceabilityRow } from '../../shared/models/requirement.model';
import { Priority } from '../../shared/models/test-case.model';
import { displayValue } from '../../shared/components/custom-fields/custom-field-values';

export type RequirementSortColumn = 'externalId' | 'title' | 'coverage' | 'priority';

export interface RequirementViewOptions {
  query: string;
  coverage: CoverageStatus | '';
  status: RequirementStatus | '';
  priority: Priority | '';
  sort: Sort;
}

/** Most urgent first. */
const PRIORITY_RANK: Record<Priority, number> = { CRITICAL: 0, HIGH: 1, MEDIUM: 2, LOW: 3 };

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
    if (needle && !searchableText(requirement).includes(needle)) {
      return false;
    }
    if (options.status && requirement.status !== options.status) {
      return false;
    }
    if (options.priority && requirement.priority !== options.priority) {
      return false;
    }
    return !options.coverage || rowsById.get(requirement.id)?.coverage === options.coverage;
  });
  return sortRows(matching, options.sort, (requirement, column) => {
    if (column === 'coverage') {
      const coverage = rowsById.get(requirement.id)?.coverage;
      return coverage ? COVERAGE_RANK[coverage] : null;
    }
    if (column === 'priority') {
      return PRIORITY_RANK[requirement.priority];
    }
    return column === 'title' ? requirement.title : requirement.externalId;
  });
}

/** Id, title and custom field values (TM18), lower-cased, for the search box. */
function searchableText(requirement: Requirement): string {
  const fieldValues = Object.values(requirement.customFields ?? {}).map(displayValue);
  return [requirement.externalId, requirement.title, ...fieldValues].join('\n').toLowerCase();
}
