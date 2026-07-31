import { createFeatureSelector, createSelector } from '@ngrx/store';
import { TestCaseState, testCaseAdapter } from './test-case.state';

export const selectTestCaseState = createFeatureSelector<TestCaseState>('testCases');

const { selectAll, selectEntities } = testCaseAdapter.getSelectors();

export const selectAllTestCases = createSelector(selectTestCaseState, selectAll);

export const selectTestCaseEntities = createSelector(selectTestCaseState, selectEntities);

export const selectTestCasesLoading = createSelector(
  selectTestCaseState,
  (state) => state.loading
);

export const selectTestCasesError = createSelector(
  selectTestCaseState,
  (state) => state.error
);

export const selectTestCaseProjectId = createSelector(
  selectTestCaseState,
  (state) => state.projectId
);

export const selectTestCasePage = createSelector(
  selectTestCaseState,
  (state) => state.page
);

export const selectTestCaseById = (id: string) =>
  createSelector(selectTestCaseEntities, (entities) => entities[id]);

export const selectSelectedTestCaseIds = createSelector(
  selectTestCaseState,
  (state) => state.selectedIds
);

export const selectHasSelection = createSelector(
  selectSelectedTestCaseIds,
  (ids) => ids.length > 0
);
