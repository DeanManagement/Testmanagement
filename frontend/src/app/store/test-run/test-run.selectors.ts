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

export const selectTestRunById = (id: string) =>
  createSelector(selectTestRunEntities, (entities) => entities[id]);

export const selectMyTestRuns = createSelector(
  selectTestRunState,
  (state) => state.myTestRuns
);

export const selectMyTestRunsLoading = createSelector(
  selectTestRunState,
  (state) => state.myTestRunsLoading
);
