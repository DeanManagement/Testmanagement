import { describe, expect, it } from 'vitest';
import { TestRun, TestResult } from '../../../shared/models/test-run.model';
import { TestRunDetailComponent } from './test-run-detail.component';

/**
 * How a finished run orders and surfaces its results.
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
  const proto = TestRunDetailComponent.prototype;

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

  const run = (results: TestResult[]) => ({ results } as TestRun);

  it('treats FAILED and BLOCKED as findings, and SKIPPED as not one', () => {
    // SKIPPED is a deliberate omission, not something that needs looking at.
    expect(proto.isFailure(result({ status: 'FAILED' }))).toBe(true);
    expect(proto.isFailure(result({ status: 'BLOCKED' }))).toBe(true);
    expect(proto.isFailure(result({ status: 'SKIPPED' }))).toBe(false);
    expect(proto.isFailure(result({ status: 'PASSED' }))).toBe(false);
  });

  it('collects only the findings for the summary', () => {
    const failures = proto.failures(
      run([
        result({ status: 'PASSED', testCaseTitle: 'fine' }),
        result({ status: 'FAILED', testCaseTitle: 'broken' }),
        result({ status: 'SKIPPED', testCaseTitle: 'not run' }),
        result({ status: 'BLOCKED', testCaseTitle: 'stuck' }),
      ])
    );

    expect(failures.map((r) => r.testCaseTitle)).toEqual(['broken', 'stuck']);
  });

  it('orders results by how much attention they need, not by execution order', () => {
    const ordered = proto.resultsWorstFirst(
      run([
        result({ status: 'PASSED', testCaseTitle: 'p' }),
        result({ status: 'SKIPPED', testCaseTitle: 's' }),
        result({ status: 'BLOCKED', testCaseTitle: 'b' }),
        result({ status: 'PENDING', testCaseTitle: 'n' }),
        result({ status: 'FAILED', testCaseTitle: 'f' }),
      ])
    );

    expect(ordered.map((r) => r.testCaseTitle)).toEqual(['f', 'b', 'n', 's', 'p']);
  });

  it('picks the first failing step, since a later one is usually just fallout', () => {
    const step = (orderIndex: number, status: string, actualResult = '') =>
      ({ id: `s${orderIndex}`, orderIndex, status, actualResult, action: `step ${orderIndex}` }) as never;

    const found = proto.firstFailedStep(
      result({
        status: 'FAILED',
        // Deliberately out of order — the component sorts before searching.
        stepResults: [
          step(2, 'FAILED', 'second failure, a consequence'),
          step(0, 'PASSED'),
          step(1, 'FAILED', 'the one that actually broke'),
        ],
      })
    );

    expect(found?.orderIndex).toBe(1);
    expect(found?.actualResult).toBe('the one that actually broke');
  });

  it('has no findings for a wholly passing run, so the summary stays hidden', () => {
    expect(proto.failures(run([result({ status: 'PASSED' }), result({ status: 'PASSED' })]))).toEqual([]);
  });

  it('copes with a run that has no results at all', () => {
    expect(proto.failures({} as TestRun)).toEqual([]);
    expect(proto.resultsWorstFirst({} as TestRun)).toEqual([]);
  });
});
