import { TestResult, TestResultStatus } from '../../../shared/models/test-run.model';
import { BugReport } from '../../../shared/models/bug-report.model';

/** A status worth a bug report (PRD-047). */
export function isFailureStatus(status: TestResultStatus): boolean {
  return status === 'FAILED' || status === 'BLOCKED';
}

/** A failed step is reason enough, even while the result itself is still pending. */
export function hasFailure(result: TestResult): boolean {
  return isFailureStatus(result.status) || result.stepResults.some((step) => isFailureStatus(step.status));
}

/** Where on this result the bug was seen: its step number, or null for the result as a whole. */
export function stepSeenAt(bug: BugReport, resultId: string): number | null {
  if (bug.testResultId === resultId) return bug.stepNumber;
  return bug.links.find((link) => link.testResultId === resultId)?.stepNumber ?? null;
}

/** Only results in {@code status}, or all of them without one (the ?status= filter). */
export function inStatus(results: TestResult[], status: TestResultStatus | null): TestResult[] {
  return status ? results.filter((result) => result.status === status) : results;
}

/**
 * How many pending steps a status would carry down to (PRD-048): only PASSED and SKIPPED cascade,
 * since a failure belongs to the step that failed. Zero means there is nothing to offer.
 */
export function cascadableSteps(result: TestResult, status: TestResultStatus): number {
  if (status !== 'PASSED' && status !== 'SKIPPED') return 0;
  return result.stepResults.filter((step) => step.status === 'PENDING').length;
}
