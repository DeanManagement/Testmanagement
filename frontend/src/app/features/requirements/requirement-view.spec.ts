import { describe, expect, it } from 'vitest';
import { viewRequirements } from './requirement-view';
import { CoverageStatus, Requirement, TraceabilityRow } from '../../shared/models/requirement.model';

function requirement(id: string, externalId: string, title: string): Requirement {
  return { id, externalId, title, description: null, testCases: [], createdAt: '', updatedAt: '' };
}

function row(requirementId: string, coverage: CoverageStatus): TraceabilityRow {
  return { requirementId, externalId: '', title: '', cells: [], coverage };
}

describe('viewRequirements', () => {
  const requirements = [
    requirement('a', 'REQ-10', 'Login works'),
    requirement('b', 'REQ-2', 'Export a report'),
    requirement('c', 'REQ-1', 'Logout'),
  ];
  const rows = new Map([
    ['a', row('a', 'PASSED')],
    ['b', row('b', 'FAILED')],
    ['c', row('c', 'UNCOVERED')],
  ]);
  const unsorted = { active: '', direction: '' as const };

  it('matches the query against the id and the title, ignoring case', () => {
    const byTitle = viewRequirements(requirements, rows, { query: 'LOG', coverage: '', sort: unsorted });
    const byId = viewRequirements(requirements, rows, { query: 'req-2', coverage: '', sort: unsorted });

    expect(byTitle.map((r) => r.id)).toEqual(['a', 'c']);
    expect(byId.map((r) => r.id)).toEqual(['b']);
  });

  it('keeps only requirements with the chosen coverage', () => {
    const result = viewRequirements(requirements, rows, { query: '', coverage: 'FAILED', sort: unsorted });

    expect(result.map((r) => r.id)).toEqual(['b']);
  });

  it('sorts ids naturally', () => {
    const result = viewRequirements(requirements, rows,
      { query: '', coverage: '', sort: { active: 'externalId', direction: 'asc' } });

    expect(result.map((r) => r.externalId)).toEqual(['REQ-1', 'REQ-2', 'REQ-10']);
  });

  it('sorts by coverage worst first', () => {
    const result = viewRequirements(requirements, rows,
      { query: '', coverage: '', sort: { active: 'coverage', direction: 'asc' } });

    expect(result.map((r) => r.id)).toEqual(['b', 'c', 'a']);
  });

  it('sorts by title', () => {
    const result = viewRequirements(requirements, rows,
      { query: '', coverage: '', sort: { active: 'title', direction: 'desc' } });

    expect(result.map((r) => r.title)).toEqual(['Logout', 'Login works', 'Export a report']);
  });
});
