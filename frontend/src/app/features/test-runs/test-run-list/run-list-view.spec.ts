import { describe, expect, it } from 'vitest';
import { readRunColumns, resultSegments, runColumns, writeRunColumns } from './run-list-view';
import { TestRun } from '../../../shared/models/test-run.model';

describe('run list view (PRD-049)', () => {
  it('puts chosen optional columns between environment and status', () => {
    expect(runColumns(new Set(['ended', 'plan'])))
      .toEqual(['key', 'name', 'environment', 'plan', 'ended', 'status', 'results', 'actions']);
  });

  it('remembers the choice across a reload', () => {
    const store = new Map<string, string>();
    const storage = { getItem: (k: string) => store.get(k) ?? null, setItem: (k: string, v: string) => void store.set(k, v) };

    writeRunColumns(storage, new Set(['executor']));

    expect([...readRunColumns(storage)]).toEqual(['executor']);
  });

  it('shows no optional columns when storage throws or holds rubbish', () => {
    const throwing = { getItem: () => { throw new Error('private mode'); } };

    expect(readRunColumns(throwing).size).toBe(0);
    expect(readRunColumns({ getItem: () => '["plan","nonsense"]' })).toEqual(new Set(['plan']));
    expect(() => writeRunColumns({ setItem: () => { throw new Error('full'); } }, new Set(['plan']))).not.toThrow();
  });

  it('splits the results into bar segments in status order, as shares of the total', () => {
    const run = { total: 10, passed: 5, failed: 2, blocked: 0, skipped: 1, pending: 2 } as TestRun;

    expect(resultSegments(run)).toEqual([
      { status: 'PASSED', count: 5, percent: 50 },
      { status: 'FAILED', count: 2, percent: 20 },
      { status: 'SKIPPED', count: 1, percent: 10 },
      { status: 'PENDING', count: 2, percent: 20 },
    ]);
    expect(resultSegments({ total: 0 } as TestRun)).toEqual([]);
  });
});
