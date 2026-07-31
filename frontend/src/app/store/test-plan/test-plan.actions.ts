import { createActionGroup, props } from '@ngrx/store';
import {
  CreateTestPlanRequest,
  TestPlan,
  TestPlanSummary,
  UpdateTestPlanRequest,
} from '../../shared/models/test-plan.model';

export const TestPlanActions = createActionGroup({
  source: 'TestPlans',
  events: {
    'Load Test Plans': props<{ projectId: string }>(),
    'Load Test Plans Success': props<{ testPlans: TestPlan[] }>(),
    'Load Test Plans Failure': props<{ error: string }>(),
    'Load Test Plan': props<{ projectId: string; id: string }>(),
    'Load Test Plan Success': props<{ testPlan: TestPlan }>(),
    'Load Test Plan Failure': props<{ error: string }>(),
    'Create Test Plan': props<{ projectId: string; request: CreateTestPlanRequest }>(),
    'Create Test Plan Success': props<{ testPlan: TestPlan }>(),
    'Create Test Plan Failure': props<{ error: string }>(),
    'Update Test Plan': props<{ projectId: string; id: string; request: UpdateTestPlanRequest }>(),
    'Update Test Plan Success': props<{ testPlan: TestPlan }>(),
    'Update Test Plan Failure': props<{ error: string }>(),
    'Delete Test Plan': props<{ projectId: string; id: string }>(),
    'Delete Test Plan Success': props<{ id: string }>(),
    'Delete Test Plan Failure': props<{ error: string }>(),
    'Load Summary': props<{ projectId: string; planId: string }>(),
    'Load Summary Success': props<{ summary: TestPlanSummary }>(),
    'Load Summary Failure': props<{ error: string }>(),
  },
});
