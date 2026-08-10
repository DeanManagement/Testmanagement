import { describe, expect, it } from 'vitest';
import { TestResult } from '../models/test-run.model';
import { failuresOf, isFailure, worstFirst } from './test-result-triage';

/**
 * How a finished run orders and surfaces its results. Shared by the run detail and the run report,
 * so a disagreement here would show four failures on one screen and three on the other.
 *
 * <p>Pure functions, tested directly rather than through the component harness: the whole point is
 * the ordering and selection logic, and rendering it needs the full run-detail component with its
 * store, router, dialogs and API services. The template bindings that consume these are trivial.
 *
 * <p>What this guards: a completed run exists to answer "what broke?". Before this, failures sat
 * collapsed among the passes in execution order, and the executor's comment — which holds the
 * actual error, and is what CI ingestion populates — was not rendered on a finished run at all.
 */
describe('completed test run — surfacing failures', () => {
  const result = (over: Partial<TestResult>): TestResult => ({
    id: over.id ?? crypto.randomUUID(),
    testCaseId: 'tc-1',
    testCaseTitle: over.testCaseTitle ?? 'A case',
    status: over.status ?? 'PASSED',
    comment: over.comment ?? '',
    defectLink: over.defectLink ?? '',
    executedVersion: null,
    parameterSetName: null,
    stepResults: over.stepResults ?? [],
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  });

    it('treats FAILED and BLOCKED as findings, and SKIPPED as not one', () => {
    // SKIPPED is a deliberate omission, not something that needs looking at.
    expect(isFailure(result({ status: 'FAILED' }))).toBe(true);
    expect(isFailure(result({ status: 'BLOCKED' }))).toBe(true);
    expect(isFailure(result({ status: 'SKIPPED' }))).toBe(false);
    expect(isFailure(result({ status: 'PASSED' }))).toBe(false);
  });

  it('collects only the findings for the summary', () => {
    const failures = failuresOf([
        result({ status: 'PASSED', testCaseTitle: 'fine' }),
        result({ status: 'FAILED', testCaseTitle: 'broken' }),
        result({ status: 'SKIPPED', testCaseTitle: 'not run' }),
        result({ status: 'BLOCKED', testCaseTitle: 'stuck' }),
    ]);

    expect(failures.map((r) => r.testCaseTitle)).toEqual(['broken', 'stuck']);
  });

  it('orders results by how much attention they need, not by execution order', () => {
    const ordered = worstFirst([
        result({ status: 'PASSED', testCaseTitle: 'p' }),
        result({ status: 'SKIPPED', testCaseTitle: 's' }),
        result({ status: 'BLOCKED', testCaseTitle: 'b' }),
        result({ status: 'PENDING', testCaseTitle: 'n' }),
        result({ status: 'FAILED', testCaseTitle: 'f' }),
    ]);

    expect(ordered.map((r) => r.testCaseTitle)).toEqual(['f', 'b', 'n', 's', 'p']);
  });

  it('has no findings for a wholly passing run, so the summary stays hidden', () => {
    expect(failuresOf([result({ status: 'PASSED' }), result({ status: 'PASSED' })])).toEqual([]);
  });

  it('copes with a run that has no results at all', () => {
    expect(failuresOf(undefined)).toEqual([]);
    expect(worstFirst(undefined)).toEqual([]);
  });
});
