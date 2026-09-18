import { ALL_TEST_CASE_STATUSES, TestCaseStatus } from '../../../shared/models/test-case.model';

/**
 * Statuses a plain edit may pick (PRD-033). Under review ACTIVE is reached only by approving,
 * so it's offered only to keep a case that already is ACTIVE unchanged.
 */
export function selectableStatuses(reviewRequired: boolean, current?: TestCaseStatus | null): TestCaseStatus[] {
  if (!reviewRequired) {
    return ALL_TEST_CASE_STATUSES;
  }
  return ALL_TEST_CASE_STATUSES.filter((status) => status !== 'ACTIVE' || current === 'ACTIVE');
}

/** Under review, ACTIVE reads as "Approved"; otherwise the plain status name. */
export function statusLabelKey(status: TestCaseStatus, reviewRequired: boolean): string {
  return reviewRequired && status === 'ACTIVE' ? 'testCaseStatus.APPROVED' : 'testCaseStatus.' + status;
}

/**
 * Members who may review under the given reviewer role. Fewer than two means an author who is
 * themselves a reviewer has nobody else to approve their cases (PRD-033 §4).
 */
export function eligibleReviewerCount(members: readonly { role: string }[], minRole: 'ADMIN' | 'TESTER'): number {
  return members.filter((member) => member.role === 'ADMIN' || (minRole === 'TESTER' && member.role === 'TESTER'))
    .length;
}
