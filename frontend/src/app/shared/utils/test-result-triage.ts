import { TestResult, TestResultStatus } from '../models/test-run.model';

/**
 * Which results of a finished run need someone's attention, and in what order.
 *
 * <p>Shared because two screens ask the same question — the run detail and the run report — and a
 * disagreement between them about what counts as a failure would be a bug that is very hard to
 * notice: the report would say four, the detail three, and both would look plausible.
 */

/**
 * FAILED and BLOCKED are both findings: in each case the test did not demonstrate what it was
 * written to demonstrate, and a person has to look. SKIPPED is not — that is a deliberate omission,
 * and treating it as a finding would bury the real ones in noise.
 */
export function isFailure(result: TestResult): boolean {
  return result.status === 'FAILED' || result.status === 'BLOCKED';
}

export function failuresOf(results: TestResult[] | undefined): TestResult[] {
  return (results ?? []).filter(isFailure);
}

/**
 * Ordered by how much attention each result needs, not by the order the cases happened to run in.
 * A finished run is opened to find out what broke.
 */
export function worstFirst(results: TestResult[] | undefined): TestResult[] {
  const rank: Record<TestResultStatus, number> = {
    FAILED: 0,
    BLOCKED: 1,
    PENDING: 2,
    SKIPPED: 3,
    PASSED: 4,
  };
  return [...(results ?? [])].sort((a, b) => (rank[a.status] ?? 9) - (rank[b.status] ?? 9));
}
