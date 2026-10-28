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

/** One line of the requirements tree (TM18). */
export interface RequirementTreeRow {
  requirement: Requirement;
  depth: number;
  hasChildren: boolean;
  /** False for an ancestor shown only so a matching child keeps its place in the tree. */
  matches: boolean;
}

/**
 * The filtered, sorted requirements as a tree: siblings in sort order, each match with its
 * ancestors, and nothing below a collapsed node. A requirement whose parent is missing is a root.
 */
export function requirementTree(
  requirements: Requirement[],
  rowsById: Map<string, TraceabilityRow>,
  options: RequirementViewOptions,
  collapsed: ReadonlySet<string>,
): RequirementTreeRow[] {
  const byId = new Map(requirements.map((r) => [r.id, r]));
  const sorted = viewRequirements(requirements, rowsById, { ...options, query: '', coverage: '', status: '', priority: '' });
  const matches = new Set(viewRequirements(requirements, rowsById, options).map((r) => r.id));

  const visible = new Set<string>();
  for (const id of matches) {
    let current = byId.get(id);
    while (current && !visible.has(current.id)) {
      visible.add(current.id);
      current = current.parentId ? byId.get(current.parentId) : undefined;
    }
  }
  const children = new Map<string | null, Requirement[]>();
  for (const requirement of sorted) {
    if (!visible.has(requirement.id)) continue;
    const parent = requirement.parentId && byId.has(requirement.parentId) ? requirement.parentId : null;
    children.set(parent, [...(children.get(parent) ?? []), requirement]);
  }

  const rows: RequirementTreeRow[] = [];
  const emitted = new Set<string>();
  const walk = (parent: string | null, depth: number): void => {
    for (const requirement of children.get(parent) ?? []) {
      if (emitted.has(requirement.id)) continue;
      emitted.add(requirement.id);
      const hasChildren = (children.get(requirement.id) ?? []).length > 0;
      rows.push({ requirement, depth, hasChildren, matches: matches.has(requirement.id) });
      if (!collapsed.has(requirement.id)) {
        walk(requirement.id, depth + 1);
      }
    }
  };
  walk(null, 0);
  return rows;
}

/** The requirement and everything below it: what may not become its parent. */
export function selfAndDescendants(requirements: Requirement[], id: string): Set<string> {
  const result = new Set([id]);
  let grew = true;
  while (grew) {
    grew = false;
    for (const requirement of requirements) {
      if (requirement.parentId && result.has(requirement.parentId) && !result.has(requirement.id)) {
        result.add(requirement.id);
        grew = true;
      }
    }
  }
  return result;
}
