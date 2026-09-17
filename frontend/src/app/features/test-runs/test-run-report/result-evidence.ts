import { TestResult } from '../../../shared/models/test-run.model';

export const NO_EVIDENCE = '-';

const WORTH_EXPLAINING: ReadonlySet<string> = new Set(['FAILED', 'BLOCKED', 'SKIPPED']);

/**
 * What the report prints to explain a result: its comment, else what was observed at each step
 * that did not pass. A result executed step by step has no comment — its evidence is on the steps
 * — and used to show as a bare dash. Mirrors `ResultEvidence` on the server, which the PDF uses.
 */
export function resultEvidence(result: Pick<TestResult, 'comment' | 'stepResults'>): string {
  if (result.comment?.trim()) {
    return result.comment;
  }
  const ordered = [...(result.stepResults ?? [])].sort((a, b) => a.orderIndex - b.orderIndex);
  const fromSteps = ordered
    .map((step, index) => ({ step, number: index + 1 }))
    .filter(({ step }) => WORTH_EXPLAINING.has(step.status) && !!step.actualResult?.trim())
    .map(({ step, number }) => `Step ${number}: ${step.actualResult}`);
  return fromSteps.length > 0 ? fromSteps.join('\n') : NO_EVIDENCE;
}
