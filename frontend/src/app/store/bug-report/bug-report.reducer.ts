import { createReducer, on } from '@ngrx/store';
import { BugReportActions } from './bug-report.actions';
import { bugReportAdapter, initialBugReportState } from './bug-report.state';

export const bugReportReducer = createReducer(
  initialBugReportState,

  on(BugReportActions.loadBugReports, (state) => ({
    ...state,
    loading: true,
    error: null,
  })),
  on(BugReportActions.loadBugReportsSuccess, (state, { bugReports }) =>
    bugReportAdapter.setAll(bugReports, { ...state, loading: false })
  ),
  on(BugReportActions.loadBugReportsFailure, (state, { error }) => ({
    ...state,
    loading: false,
    error,
  })),

  on(BugReportActions.loadBugReport, (state) => ({
    ...state,
    loading: true,
    error: null,
  })),
  on(BugReportActions.loadBugReportSuccess, (state, { bugReport }) =>
    bugReportAdapter.upsertOne(bugReport, { ...state, loading: false })
  ),
  on(BugReportActions.loadBugReportFailure, (state, { error }) => ({
    ...state,
    loading: false,
    error,
  })),

  on(BugReportActions.loadBugReportsByTestResultSuccess, (state, { bugReports }) => ({
    ...state,
    linkedBugReports: bugReports,
  })),

  on(BugReportActions.createBugReportSuccess, (state, { bugReport }) =>
    bugReportAdapter.addOne(bugReport, { ...state, loading: false })
  ),
  on(BugReportActions.createBugReportFailure, (state, { error }) => ({
    ...state,
    loading: false,
    error,
  })),

  on(BugReportActions.updateBugReportSuccess, (state, { bugReport }) =>
    bugReportAdapter.upsertOne(bugReport, { ...state, loading: false })
  ),
  on(BugReportActions.updateBugReportFailure, (state, { error }) => ({
    ...state,
    loading: false,
    error,
  })),

  on(BugReportActions.deleteBugReportSuccess, (state, { id }) =>
    bugReportAdapter.removeOne(id, state)
  ),
  on(BugReportActions.deleteBugReportFailure, (state, { error }) => ({
    ...state,
    error,
  })),

  on(BugReportActions.loadMyBugReports, (state) => ({
    ...state,
    myBugReportsLoading: true,
  })),
  on(BugReportActions.loadMyBugReportsSuccess, (state, { bugReports }) => ({
    ...state,
    myBugReports: bugReports,
    myBugReportsLoading: false,
  })),
  on(BugReportActions.loadMyBugReportsFailure, (state, { error }) => ({
    ...state,
    myBugReportsLoading: false,
    error,
  })),

  on(BugReportActions.clearBugReports, () => initialBugReportState),
);
