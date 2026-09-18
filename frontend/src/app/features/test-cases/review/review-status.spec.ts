import { describe, expect, it } from 'vitest';
import { eligibleReviewerCount, selectableStatuses, statusLabelKey } from './review-status';

describe('selectableStatuses', () => {
  describe('when review is not required', () => {
    it('should offer every status', () => {
      expect(selectableStatuses(false)).toEqual(['DRAFT', 'IN_REVIEW', 'ACTIVE', 'DEPRECATED']);
    });
  });

  describe('when review is required', () => {
    it('should not offer ACTIVE, which only approving can set', () => {
      expect(selectableStatuses(true, 'DRAFT')).toEqual(['DRAFT', 'IN_REVIEW', 'DEPRECATED']);
    });

    it('should keep ACTIVE for a case that already is, so saving it unchanged works', () => {
      expect(selectableStatuses(true, 'ACTIVE')).toContain('ACTIVE');
    });
  });
});

describe('statusLabelKey', () => {
  it('should call ACTIVE "approved" under review', () => {
    expect(statusLabelKey('ACTIVE', true)).toBe('testCaseStatus.APPROVED');
  });

  it('should keep the plain name otherwise', () => {
    expect(statusLabelKey('ACTIVE', false)).toBe('testCaseStatus.ACTIVE');
    expect(statusLabelKey('IN_REVIEW', true)).toBe('testCaseStatus.IN_REVIEW');
  });
});

describe('eligibleReviewerCount', () => {
  const members = [{ role: 'ADMIN' }, { role: 'TESTER' }, { role: 'TESTER' }, { role: 'VIEWER' }];

  it('should count only admins when reviewers must be admins', () => {
    expect(eligibleReviewerCount(members, 'ADMIN')).toBe(1);
  });

  it('should count testers too when testers may review, but never viewers', () => {
    expect(eligibleReviewerCount(members, 'TESTER')).toBe(3);
  });
});
