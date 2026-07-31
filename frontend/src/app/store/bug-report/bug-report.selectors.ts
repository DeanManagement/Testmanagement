import { createFeatureSelector, createSelector } from '@ngrx/store';
import { bugReportAdapter, BugReportState } from './bug-report.state';

export const selectBugReportState = createFeatureSelector<BugReportState>('bugReports');

const { selectAll, selectEntities } = bugReportAdapter.getSelectors();

export const selectAllBugReports = createSelector(selectBugReportState, selectAll);
export const selectBugReportEntities = createSelector(selectBugReportState, selectEntities);
export const selectBugReportsLoading = createSelector(selectBugReportState, (state) => state.loading);
export const selectBugReportsError = createSelector(selectBugReportState, (state) => state.error);
export const selectLinkedBugReportsByResult = createSelector(
  selectBugReportState,
  (state) => state.linkedBugReportsByResult,
);

/**
 * Bug reports linked to a single test result. Returns `[]` if not loaded.
 * Use `selectLinkedBugReportsLoadedFor` to distinguish "not yet fetched" from
 * "fetched, empty result."
 */
export const selectLinkedBugReportsFor = (testResultId: string | null) =>
  createSelector(selectLinkedBugReportsByResult, (byResult) =>
    testResultId ? byResult[testResultId] ?? [] : []
  );

export const selectLinkedBugReportsLoadedFor = (testResultId: string | null) =>
  createSelector(selectLinkedBugReportsByResult, (byResult) =>
    testResultId ? Object.prototype.hasOwnProperty.call(byResult, testResultId) : false
  );

export const selectBugReportById = (id: string) =>
  createSelector(selectBugReportEntities, (entities) => entities[id]);

export const selectMyBugReports = createSelector(selectBugReportState, (state) => state.myBugReports);
export const selectMyBugReportsLoading = createSelector(selectBugReportState, (state) => state.myBugReportsLoading);
