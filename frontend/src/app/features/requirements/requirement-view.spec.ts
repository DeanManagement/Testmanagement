import { describe, expect, it } from 'vitest';
import { viewRequirements } from './requirement-view';
import { CoverageStatus, Requirement, TraceabilityRow } from '../../shared/models/requirement.model';

function requirement(id: string, externalId: string, title: string, over: Partial<Requirement> = {}): Requirement {
  return {
    id, externalId, title, description: null, status: 'DRAFT', priority: 'MEDIUM', customFields: {}, testCases: [],
    createdAt: '', updatedAt: '', ...over,
  };
}

function row(requirementId: string, coverage: CoverageStatus): TraceabilityRow {
  return { requirementId, externalId: '', title: '', status: 'DRAFT', priority: 'MEDIUM', cells: [], coverage };
}

const none = { coverage: '' as const, status: '' as const, priority: '' as const };

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
    const byTitle = viewRequirements(requirements, rows, { ...none, query: 'LOG', sort: unsorted });
    const byId = viewRequirements(requirements, rows, { ...none, query: 'req-2', sort: unsorted });

    expect(byTitle.map((r) => r.id)).toEqual(['a', 'c']);
    expect(byId.map((r) => r.id)).toEqual(['b']);
  });

  it('keeps only requirements with the chosen coverage', () => {
    const result = viewRequirements(requirements, rows, { ...none, query: '', coverage: 'FAILED', sort: unsorted });

    expect(result.map((r) => r.id)).toEqual(['b']);
  });

  it('sorts ids naturally', () => {
    const result = viewRequirements(requirements, rows,
      { ...none, query: '', sort: { active: 'externalId', direction: 'asc' } });

    expect(result.map((r) => r.externalId)).toEqual(['REQ-1', 'REQ-2', 'REQ-10']);
  });

  it('sorts by coverage worst first', () => {
    const result = viewRequirements(requirements, rows,
      { ...none, query: '', sort: { active: 'coverage', direction: 'asc' } });

    expect(result.map((r) => r.id)).toEqual(['b', 'c', 'a']);
  });

  it('keeps only requirements with the chosen status and priority (TM18)', () => {
    const mixed = [
      requirement('a', 'REQ-1', 'A', { status: 'APPROVED', priority: 'HIGH' }),
      requirement('b', 'REQ-2', 'B', { status: 'APPROVED', priority: 'LOW' }),
      requirement('c', 'REQ-3', 'C', { status: 'OBSOLETE', priority: 'HIGH' }),
    ];

    const result = viewRequirements(mixed, rows, { ...none, query: '', status: 'APPROVED', priority: 'HIGH', sort: unsorted });

    expect(result.map((r) => r.id)).toEqual(['a']);
  });

  it('matches the query against custom field values too', () => {
    const tagged = [requirement('a', 'REQ-1', 'A', { customFields: { Component: 'Checkout', Tags: ['Payments'] } }),
      requirement('b', 'REQ-2', 'B')];

    expect(viewRequirements(tagged, rows, { ...none, query: 'payments', sort: unsorted }).map((r) => r.id)).toEqual(['a']);
  });

  it('sorts by priority, most urgent first', () => {
    const mixed = [requirement('a', 'REQ-1', 'A', { priority: 'LOW' }),
      requirement('b', 'REQ-2', 'B', { priority: 'CRITICAL' })];

    const result = viewRequirements(mixed, rows, { ...none, query: '', sort: { active: 'priority', direction: 'asc' } });

    expect(result.map((r) => r.id)).toEqual(['b', 'a']);
  });

  it('sorts by title', () => {
    const result = viewRequirements(requirements, rows,
      { ...none, query: '', sort: { active: 'title', direction: 'desc' } });

    expect(result.map((r) => r.title)).toEqual(['Logout', 'Login works', 'Export a report']);
  });
});
