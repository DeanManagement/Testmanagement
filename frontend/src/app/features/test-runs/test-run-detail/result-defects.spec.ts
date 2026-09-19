import { describe, expect, it } from 'vitest';
import { cascadableSteps, hasFailure, inStatus, stepSeenAt } from './result-defects';
import { StepResult, TestResult } from '../../../shared/models/test-run.model';
import { BugReport } from '../../../shared/models/bug-report.model';

/** PRD-047: when the execution view offers bug actions, and where it says a bug was seen. */
describe('result defects', () => {
  const result = (status: TestResult['status'], ...steps: StepResult['status'][]) =>
    ({ id: 'r1', status, stepResults: steps.map((s) => ({ status: s })) }) as TestResult;

  it('offers bug actions for a failed result, and for a pending one with a failed or blocked step', () => {
    expect(hasFailure(result('FAILED'))).toBe(true);
    expect(hasFailure(result('PENDING', 'PASSED', 'FAILED'))).toBe(true);
    expect(hasFailure(result('PENDING', 'BLOCKED'))).toBe(true);
    expect(hasFailure(result('PASSED', 'PASSED', 'SKIPPED'))).toBe(false);
  });

  it('names the found-in step, or the step of a later occurrence on this result', () => {
    const found = { testResultId: 'r1', stepNumber: 2, links: [] } as unknown as BugReport;
    const linked = { testResultId: 'r0', stepNumber: 5, links: [{ testResultId: 'r1', stepNumber: 3 }] } as unknown as BugReport;

    expect(stepSeenAt(found, 'r1')).toBe(2);
    expect(stepSeenAt(linked, 'r1')).toBe(3);
    expect(stepSeenAt(linked, 'r9')).toBeNull();
  });

  it('keeps only results in the requested status, or all without one', () => {
    const results = [result('FAILED'), result('PASSED')];

    expect(inStatus(results, 'FAILED')).toEqual([results[0]]);
    expect(inStatus(results, null)).toEqual(results);
  });

  it('offers to cascade a pass or skip only to the steps still pending (PRD-048)', () => {
    const r = result('PENDING', 'PENDING', 'FAILED', 'PENDING');

    expect(cascadableSteps(r, 'PASSED')).toBe(2);
    expect(cascadableSteps(r, 'SKIPPED')).toBe(2);
    expect(cascadableSteps(r, 'FAILED')).toBe(0);
    expect(cascadableSteps(result('PENDING', 'PASSED'), 'PASSED')).toBe(0);
  });
});
