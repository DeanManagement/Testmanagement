import { createActionGroup, emptyProps, props } from '@ngrx/store';
import { CreateTestCaseRequest, TestCase, UpdateTestCaseRequest } from '../../shared/models/test-case.model';

export const TestCaseActions = createActionGroup({
  source: 'TestCases',
  events: {
    'Load Test Cases': props<{ projectId: string }>(),
    'Load Test Cases Success': props<{ testCases: TestCase[] }>(),
    'Load Test Cases Failure': props<{ error: string }>(),
    'Create Test Case': props<{ projectId: string; request: CreateTestCaseRequest }>(),
    'Create Test Case Success': props<{ testCase: TestCase }>(),
    'Create Test Case Failure': props<{ error: string }>(),
    'Update Test Case': props<{ projectId: string; id: string; request: UpdateTestCaseRequest }>(),
    'Update Test Case Success': props<{ testCase: TestCase }>(),
    'Update Test Case Failure': props<{ error: string }>(),
    'Delete Test Case': props<{ projectId: string; id: string }>(),
    'Delete Test Case Success': props<{ id: string }>(),
    'Delete Test Case Failure': props<{ error: string }>(),
  },
});
