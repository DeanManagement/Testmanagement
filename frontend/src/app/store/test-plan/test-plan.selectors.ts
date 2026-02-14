import { createFeatureSelector, createSelector } from '@ngrx/store';
import { TestPlanState, testPlanAdapter } from './test-plan.state';

export const selectTestPlanState = createFeatureSelector<TestPlanState>('testPlans');

const { selectAll, selectEntities } = testPlanAdapter.getSelectors();

export const selectAllTestPlans = createSelector(selectTestPlanState, selectAll);

export const selectTestPlanEntities = createSelector(selectTestPlanState, selectEntities);

export const selectTestPlansLoading = createSelector(
  selectTestPlanState,
  (state) => state.loading
);

export const selectTestPlansError = createSelector(
  selectTestPlanState,
  (state) => state.error
);

export const selectTestPlanProjectId = createSelector(
  selectTestPlanState,
  (state) => state.projectId
);

export const selectTestPlanById = (id: string) =>
  createSelector(selectTestPlanEntities, (entities) => entities[id]);
