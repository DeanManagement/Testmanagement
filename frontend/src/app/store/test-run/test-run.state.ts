import { EntityState, createEntityAdapter } from '@ngrx/entity';
import { TestRun } from '../../shared/models/test-run.model';

export const testRunAdapter = createEntityAdapter<TestRun>();

export interface TestRunState extends EntityState<TestRun> {
  loading: boolean;
  error: string | null;
  projectId: string | null;
}

export const initialTestRunState: TestRunState = testRunAdapter.getInitialState({
  loading: false,
  error: null,
  projectId: null,
});
