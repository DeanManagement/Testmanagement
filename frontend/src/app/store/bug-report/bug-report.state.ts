import { createEntityAdapter, EntityState } from '@ngrx/entity';
import { BugReport } from '../../shared/models/bug-report.model';

export const bugReportAdapter = createEntityAdapter<BugReport>();

export interface BugReportState extends EntityState<BugReport> {
  loading: boolean;
  error: string | null;
  linkedBugReports: BugReport[];
}

export const initialBugReportState: BugReportState = bugReportAdapter.getInitialState({
  loading: false,
  error: null,
  linkedBugReports: [],
});
