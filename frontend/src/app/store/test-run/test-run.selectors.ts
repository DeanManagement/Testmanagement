import { createFeatureSelector, createSelector } from '@ngrx/store';
import { TestRunState, testRunAdapter } from './test-run.state';

export const selectTestRunState = createFeatureSelector<TestRunState>('testRuns');

const { selectAll, selectEntities } = testRunAdapter.getSelectors();

export const selectAllTestRuns = createSelector(selectTestRunState, selectAll);

export const selectTestRunEntities = createSelector(selectTestRunState, selectEntities);

export const selectTestRunsLoading = createSelector(
  selectTestRunState,
  (state) => state.loading
);

export const selectTestRunsError = createSelector(
  selectTestRunState,
  (state) => state.error
);

export const selectTestRunProjectId = createSelector(
  selectTestRunState,
  (state) => state.projectId
);

export const selectTestRunPage = createSelector(
  selectTestRunState,
  (state) => state.page
);

export const selectTestRunById = (id: string) =>
  createSelector(selectTestRunEntities, (entities) => entities[id]);

export const selectMyActiveTestRuns = createSelector(
  selectTestRunState,
  (state) => state.myActiveTestRuns
);

export const selectMyActiveTestRunsLoading = createSelector(
  selectTestRunState,
  (state) => state.myActiveTestRunsLoading
);

export const selectMyInProgressTestRuns = createSelector(
  selectMyActiveTestRuns,
  (runs) => runs.filter((r) => r.status === 'IN_PROGRESS')
);

export const selectMyPlannedTestRuns = createSelector(
  selectMyActiveTestRuns,
  (runs) => runs.filter((r) => r.status === 'PLANNED')
);

export const selectMyCompletedTestRuns = createSelector(
  selectTestRunState,
  (state) => state.myCompletedTestRuns
);

export const selectMyCompletedTestRunsLoading = createSelector(
  selectTestRunState,
  (state) => state.myCompletedTestRunsLoading
);

export const selectMyCompletedTestRunsLoaded = createSelector(
  selectTestRunState,
  (state) => state.myCompletedTestRunsLoaded
);
