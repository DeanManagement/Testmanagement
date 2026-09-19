import { describe, expect, it } from 'vitest';
import { selectBugReportByIdOrKey } from './bug-report.selectors';
import { bugReportAdapter, initialBugReportState } from './bug-report.state';
import { BugReport } from '../../shared/models/bug-report.model';

/** TES-BUG-19: a URL may name a bug by UUID or by key; the store keeps it under its UUID. */
describe('selectBugReportByIdOrKey', () => {
  const bug = { id: '0b1c', key: 'TES-BUG-11', title: 'Pending until reload' } as BugReport;
  const state = { bugReports: bugReportAdapter.setAll([bug], initialBugReportState) };

  it('finds a bug by its UUID', () => {
    expect(selectBugReportByIdOrKey('0b1c')(state)).toBe(bug);
  });

  it('finds a bug by its key', () => {
    expect(selectBugReportByIdOrKey('TES-BUG-11')(state)).toBe(bug);
  });

  it('finds nothing for an unknown reference', () => {
    expect(selectBugReportByIdOrKey('TES-BUG-99')(state)).toBeUndefined();
  });
});
