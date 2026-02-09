import { createReducer, on } from '@ngrx/store';
import { TestRunActions } from './test-run.actions';
import { initialTestRunState, testRunAdapter } from './test-run.state';
import { TestRun } from '../../shared/models/test-run.model';

function updateStepInRun(
  run: TestRun,
  resultId: string,
  stepResultId: string,
  updater: (step: any) => any
): TestRun {
  return {
    ...run,
    results: run.results.map((r) =>
      r.id === resultId
        ? {
            ...r,
            stepResults: r.stepResults.map((sr) =>
              sr.id === stepResultId ? updater(sr) : sr
            ),
          }
        : r
    ),
  };
}

export const testRunReducer = createReducer(
  initialTestRunState,

  on(TestRunActions.loadTestRuns, (state, { projectId }) => ({
    ...state,
    loading: true,
    error: null,
    projectId,
  })),

  on(TestRunActions.loadTestRunsSuccess, (state, { testRuns }) =>
    testRunAdapter.setAll(testRuns, { ...state, loading: false })
  ),

  on(TestRunActions.loadTestRunsFailure, (state, { error }) => ({
    ...state,
    loading: false,
    error,
  })),

  on(TestRunActions.loadTestRun, (state) => ({
    ...state,
    loading: true,
    error: null,
  })),

  on(TestRunActions.loadTestRunSuccess, (state, { testRun }) =>
    testRunAdapter.upsertOne(testRun, { ...state, loading: false })
  ),

  on(TestRunActions.loadTestRunFailure, (state, { error }) => ({
    ...state,
    loading: false,
    error,
  })),

  on(TestRunActions.createTestRunSuccess, (state, { testRun }) =>
    testRunAdapter.addOne(testRun, state)
  ),

  on(TestRunActions.createTestRunFailure, (state, { error }) => ({
    ...state,
    error,
  })),

  on(TestRunActions.updateTestRunSuccess, (state, { testRun }) =>
    testRunAdapter.upsertOne(testRun, state)
  ),

  on(TestRunActions.updateTestRunFailure, (state, { error }) => ({
    ...state,
    error,
  })),

  on(TestRunActions.deleteTestRunSuccess, (state, { id }) =>
    testRunAdapter.removeOne(id, state)
  ),

  on(TestRunActions.deleteTestRunFailure, (state, { error }) => ({
    ...state,
    error,
  })),

  on(TestRunActions.updateTestResultSuccess, (state, { runId, result }) => {
    const run = state.entities[runId];
    if (!run) return state;
    const updatedResults = run.results.map((r) =>
      r.id === result.id ? result : r
    );
    return testRunAdapter.upsertOne(
      { ...run, results: updatedResults },
      state
    );
  }),

  on(TestRunActions.updateTestResultFailure, (state, { error }) => ({
    ...state,
    error,
  })),

  on(TestRunActions.updateStepResultSuccess, (state, { runId, resultId, stepResult }) => {
    const run = state.entities[runId];
    if (!run) return state;
    return testRunAdapter.upsertOne(
      updateStepInRun(run, resultId, stepResult.id, () => stepResult),
      state
    );
  }),

  on(TestRunActions.updateStepResultFailure, (state, { error }) => ({
    ...state,
    error,
  })),

  on(TestRunActions.uploadScreenshotSuccess, (state, { runId, resultId, stepResultId, screenshotId }) => {
    const run = state.entities[runId];
    if (!run) return state;
    return testRunAdapter.upsertOne(
      updateStepInRun(run, resultId, stepResultId, (sr) => ({ ...sr, screenshotId })),
      state
    );
  }),

  on(TestRunActions.uploadScreenshotFailure, (state, { error }) => ({
    ...state,
    error,
  })),

  on(TestRunActions.deleteScreenshotSuccess, (state, { runId, resultId, stepResultId }) => {
    const run = state.entities[runId];
    if (!run) return state;
    return testRunAdapter.upsertOne(
      updateStepInRun(run, resultId, stepResultId, (sr) => ({ ...sr, screenshotId: null })),
      state
    );
  }),

  on(TestRunActions.deleteScreenshotFailure, (state, { error }) => ({
    ...state,
    error,
  })),

  on(TestRunActions.cloneTestRunSuccess, (state, { testRun }) =>
    testRunAdapter.addOne(testRun, state)
  ),

  on(TestRunActions.cloneTestRunFailure, (state, { error }) => ({
    ...state,
    error,
  }))
);
