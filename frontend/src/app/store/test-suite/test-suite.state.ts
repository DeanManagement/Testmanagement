import { EntityState, createEntityAdapter } from '@ngrx/entity';
import { TestSuite } from '../../shared/models/test-suite.model';

export const testSuiteAdapter = createEntityAdapter<TestSuite>();

export interface TestSuiteState extends EntityState<TestSuite> {
  loading: boolean;
  error: string | null;
  projectId: string | null;
}

export const initialTestSuiteState: TestSuiteState = testSuiteAdapter.getInitialState({
  loading: false,
  error: null,
  projectId: null,
});
