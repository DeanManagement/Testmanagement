import { createReducer, on } from '@ngrx/store';
import { TestCaseActions } from './test-case.actions';
import { initialTestCaseState, testCaseAdapter } from './test-case.state';

export const testCaseReducer = createReducer(
  initialTestCaseState,

  // Remembered from every action that names a project, not just the list — see the same block
  // in test-run.reducer.ts for what went wrong when a page was opened directly.
  on(
    TestCaseActions.loadTestCases,
    TestCaseActions.loadTestCase,
    TestCaseActions.createTestCase,
    TestCaseActions.updateTestCase,
    TestCaseActions.deleteTestCase,
    (state, { projectId }) => (state.projectId === projectId ? state : { ...state, projectId })
  ),

  on(TestCaseActions.loadTestCases, (state) => ({
    ...state,
    loading: true,
    error: null,
  })),

  on(TestCaseActions.loadTestCasesSuccess, (state, { testCases, page }) =>
    testCaseAdapter.setAll(testCases, { ...state, loading: false, page })
  ),

  on(TestCaseActions.loadTestCaseSuccess, (state, { testCase }) =>
    testCaseAdapter.upsertOne(testCase, state)
  ),

  on(TestCaseActions.loadTestCasesFailure, (state, { error }) => ({
    ...state,
    loading: false,
    error,
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
  })),

  on(TestCaseActions.toggleSelectTestCase, (state, { id }) => ({
    ...state,
    selectedIds: state.selectedIds.includes(id)
      ? state.selectedIds.filter(sid => sid !== id)
      : [...state.selectedIds, id],
  })),

  on(TestCaseActions.selectAllTestCases, (state, { ids }) => ({
    ...state,
    selectedIds: ids,
  })),

  on(TestCaseActions.deselectAllTestCases, (state) => ({
    ...state,
    selectedIds: [],
  })),

  on(TestCaseActions.bulkUpdateStatusSuccess, (state) => ({
    ...state,
    selectedIds: [],
  })),

  on(TestCaseActions.bulkUpdateStatusFailure, (state, { error }) => ({
    ...state,
    error,
  })),

  on(TestCaseActions.bulkDeleteSuccess, (state) => ({
    ...state,
    selectedIds: [],
  })),

  on(TestCaseActions.bulkDeleteFailure, (state, { error }) => ({
    ...state,
    error,
  }))
);
