import { createFeatureSelector, createSelector } from '@ngrx/store';
import { bugReportAdapter, BugReportState } from './bug-report.state';

export const selectBugReportState = createFeatureSelector<BugReportState>('bugReports');

const { selectAll, selectEntities } = bugReportAdapter.getSelectors();

export const selectAllBugReports = createSelector(selectBugReportState, selectAll);
export const selectBugReportEntities = createSelector(selectBugReportState, selectEntities);
export const selectBugReportsLoading = createSelector(selectBugReportState, (state) => state.loading);
export const selectBugReportsError = createSelector(selectBugReportState, (state) => state.error);
export const selectLinkedBugReports = createSelector(selectBugReportState, (state) => state.linkedBugReports);

export const selectBugReportById = (id: string) =>
  createSelector(selectBugReportEntities, (entities) => entities[id]);

export const selectMyBugReports = createSelector(selectBugReportState, (state) => state.myBugReports);
export const selectMyBugReportsLoading = createSelector(selectBugReportState, (state) => state.myBugReportsLoading);
