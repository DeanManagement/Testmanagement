import { EntityState, createEntityAdapter } from '@ngrx/entity';
import { TestCase } from '../../shared/models/test-case.model';

export const testCaseAdapter = createEntityAdapter<TestCase>();

export interface TestCaseState extends EntityState<TestCase> {
  loading: boolean;
  error: string | null;
  projectId: string | null;
  selectedIds: string[];
}

export const initialTestCaseState: TestCaseState = testCaseAdapter.getInitialState({
  loading: false,
  error: null,
  projectId: null,
  selectedIds: [],
});
