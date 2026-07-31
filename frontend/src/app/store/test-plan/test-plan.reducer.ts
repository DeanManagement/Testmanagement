import { createReducer, on } from '@ngrx/store';
import { TestPlanActions } from './test-plan.actions';
import { initialTestPlanState, testPlanAdapter } from './test-plan.state';

export const testPlanReducer = createReducer(
  initialTestPlanState,

  on(TestPlanActions.loadTestPlans, (state, { projectId }) => ({
    ...state,
    loading: true,
    error: null,
    projectId,
  })),

  on(TestPlanActions.loadTestPlansSuccess, (state, { testPlans }) =>
    testPlanAdapter.setAll(testPlans, { ...state, loading: false })
  ),

  on(TestPlanActions.loadTestPlansFailure, (state, { error }) => ({
    ...state,
    loading: false,
    error,
  })),

  on(TestPlanActions.loadTestPlan, (state, { projectId }) => ({
    ...state,
    loading: true,
    error: null,
    projectId,
  })),

  on(TestPlanActions.loadTestPlanSuccess, (state, { testPlan }) =>
    testPlanAdapter.upsertOne(testPlan, { ...state, loading: false })
  ),

  on(TestPlanActions.loadTestPlanFailure, (state, { error }) => ({
    ...state,
    loading: false,
    error,
  })),

  on(TestPlanActions.createTestPlanSuccess, (state, { testPlan }) =>
    testPlanAdapter.addOne(testPlan, state)
  ),

  on(TestPlanActions.createTestPlanFailure, (state, { error }) => ({
    ...state,
    error,
  })),

  on(TestPlanActions.updateTestPlanSuccess, (state, { testPlan }) =>
    testPlanAdapter.upsertOne(testPlan, state)
  ),

  on(TestPlanActions.updateTestPlanFailure, (state, { error }) => ({
    ...state,
    error,
  })),

  on(TestPlanActions.deleteTestPlanSuccess, (state, { id }) =>
    testPlanAdapter.removeOne(id, state)
  ),

  on(TestPlanActions.deleteTestPlanFailure, (state, { error }) => ({
    ...state,
    error,
  }))
);
