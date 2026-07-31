import { createFeatureSelector, createSelector } from '@ngrx/store';
import { TestSuiteState, testSuiteAdapter } from './test-suite.state';

export const selectTestSuiteState = createFeatureSelector<TestSuiteState>('testSuites');

const { selectAll, selectEntities } = testSuiteAdapter.getSelectors();

export const selectAllTestSuites = createSelector(selectTestSuiteState, selectAll);

export const selectTestSuiteEntities = createSelector(selectTestSuiteState, selectEntities);

export const selectTestSuitesLoading = createSelector(
  selectTestSuiteState,
  (state) => state.loading
);

export const selectTestSuitesError = createSelector(
  selectTestSuiteState,
  (state) => state.error
);

export const selectTestSuiteProjectId = createSelector(
  selectTestSuiteState,
  (state) => state.projectId
);

export const selectTestSuitePage = createSelector(
  selectTestSuiteState,
  (state) => state.page
);

export const selectTestSuiteById = (id: string) =>
  createSelector(selectTestSuiteEntities, (entities) => entities[id]);
