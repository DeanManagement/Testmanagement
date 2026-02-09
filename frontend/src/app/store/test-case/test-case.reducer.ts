import { createReducer, on } from '@ngrx/store';
import { TestCaseActions } from './test-case.actions';
import { initialTestCaseState, testCaseAdapter } from './test-case.state';

export const testCaseReducer = createReducer(
  initialTestCaseState,

  on(TestCaseActions.loadTestCases, (state, { projectId }) => ({
    ...state,
    loading: true,
    error: null,
    projectId,
  })),

  on(TestCaseActions.loadTestCasesSuccess, (state, { testCases }) =>
    testCaseAdapter.setAll(testCases, { ...state, loading: false })
  ),

  on(TestCaseActions.loadTestCasesFailure, (state, { error }) => ({
    ...state,
    loading: false,
    error,
  })),

  on(TestCaseActions.createTestCase, (state, { projectId }) => ({
    ...state,
    projectId,
  })),

  on(TestCaseActions.createTestCaseSuccess, (state, { testCase }) =>
    testCaseAdapter.addOne(testCase, state)
  ),

  on(TestCaseActions.createTestCaseFailure, (state, { error }) => ({
    ...state,
    error,
  })),

  on(TestCaseActions.updateTestCaseSuccess, (state, { testCase }) =>
    testCaseAdapter.upsertOne(testCase, state)
  ),

  on(TestCaseActions.updateTestCaseFailure, (state, { error }) => ({
    ...state,
    error,
  })),

  on(TestCaseActions.deleteTestCaseSuccess, (state, { id }) =>
    testCaseAdapter.removeOne(id, state)
  ),

  on(TestCaseActions.deleteTestCaseFailure, (state, { error }) => ({
    ...state,
    error,
  }))
);
