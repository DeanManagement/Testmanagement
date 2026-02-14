import { EntityState, createEntityAdapter } from '@ngrx/entity';
import { TestPlan } from '../../shared/models/test-plan.model';

export const testPlanAdapter = createEntityAdapter<TestPlan>();

export interface TestPlanState extends EntityState<TestPlan> {
  loading: boolean;
  error: string | null;
  projectId: string | null;
}

export const initialTestPlanState: TestPlanState = testPlanAdapter.getInitialState({
  loading: false,
  error: null,
  projectId: null,
});
