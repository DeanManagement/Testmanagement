import { createReducer, on } from '@ngrx/store';
import { TestSuiteActions } from './test-suite.actions';
import { initialTestSuiteState, testSuiteAdapter } from './test-suite.state';

export const testSuiteReducer = createReducer(
  initialTestSuiteState,

  on(TestSuiteActions.loadTestSuites, (state, { projectId }) => ({
    ...state,
    loading: true,
    error: null,
    projectId,
  })),

  on(TestSuiteActions.loadTestSuitesSuccess, (state, { testSuites }) =>
    testSuiteAdapter.setAll(testSuites, { ...state, loading: false })
  ),

  on(TestSuiteActions.loadTestSuitesFailure, (state, { error }) => ({
    ...state,
    loading: false,
    error,
  })),

  on(TestSuiteActions.createTestSuiteSuccess, (state, { testSuite }) =>
    testSuiteAdapter.addOne(testSuite, state)
  ),

  on(TestSuiteActions.createTestSuiteFailure, (state, { error }) => ({
    ...state,
    error,
  })),

  on(TestSuiteActions.updateTestSuiteSuccess, (state, { testSuite }) =>
    testSuiteAdapter.upsertOne(testSuite, state)
  ),

  on(TestSuiteActions.updateTestSuiteFailure, (state, { error }) => ({
    ...state,
    error,
  })),

  on(TestSuiteActions.deleteTestSuiteSuccess, (state, { id }) =>
    testSuiteAdapter.removeOne(id, state)
  ),

  on(TestSuiteActions.deleteTestSuiteFailure, (state, { error }) => ({
    ...state,
    error,
  }))
);
