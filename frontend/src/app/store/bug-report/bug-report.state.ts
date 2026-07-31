import { createEntityAdapter, EntityState } from '@ngrx/entity';
import { BugReport } from '../../shared/models/bug-report.model';

export const bugReportAdapter = createEntityAdapter<BugReport>();

export interface BugReportState extends EntityState<BugReport> {
  loading: boolean;
  error: string | null;
  /**
   * Bug reports linked to a given test result, indexed by `testResultId`.
   * Populated by `loadBugReportsByTestResultSuccess`. Keeping this as a
   * map (rather than a single array) lets the run-detail view click through
   * results without re-fetching the linked bugs every time.
   */
  linkedBugReportsByResult: Record<string, BugReport[]>;
  myBugReports: BugReport[];
  myBugReportsLoading: boolean;
}

export const initialBugReportState: BugReportState = bugReportAdapter.getInitialState({
  loading: false,
  error: null,
  linkedBugReportsByResult: {},
  myBugReports: [],
  myBugReportsLoading: false,
});
