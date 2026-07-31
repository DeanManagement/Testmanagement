import { createActionGroup, emptyProps, props } from '@ngrx/store';
import { CreateTestCaseRequest, TestCase, TestCaseQuery, TestCaseStatus, UpdateTestCaseRequest } from '../../shared/models/test-case.model';
import { PageMetadata } from '../../shared/models/page.model';

export const TestCaseActions = createActionGroup({
  source: 'TestCases',
  events: {
    'Load Test Cases': props<{ projectId: string; query?: TestCaseQuery }>(),
    'Load Test Cases Success': props<{ testCases: TestCase[]; page: PageMetadata }>(),
    'Load Test Cases Failure': props<{ error: string }>(),
    'Load Test Case': props<{ projectId: string; id: string }>(),
    'Load Test Case Success': props<{ testCase: TestCase }>(),
    'Load Test Case Failure': props<{ error: string }>(),
    'Create Test Case': props<{ projectId: string; request: CreateTestCaseRequest }>(),
    'Create Test Case Success': props<{ testCase: TestCase }>(),
    'Create Test Case Failure': props<{ error: string }>(),
    'Update Test Case': props<{ projectId: string; id: string; request: UpdateTestCaseRequest }>(),
    'Update Test Case Success': props<{ testCase: TestCase }>(),
    'Update Test Case Failure': props<{ error: string }>(),
    'Delete Test Case': props<{ projectId: string; id: string }>(),
    'Delete Test Case Success': props<{ id: string }>(),
    'Delete Test Case Failure': props<{ error: string }>(),
    'Toggle Select Test Case': props<{ id: string }>(),
    'Select All Test Cases': props<{ ids: string[] }>(),
    'Deselect All Test Cases': emptyProps(),
    'Bulk Update Status': props<{ projectId: string; testCaseIds: string[]; status: TestCaseStatus }>(),
    'Bulk Update Status Success': props<{ projectId: string }>(),
    'Bulk Update Status Failure': props<{ error: string }>(),
    'Bulk Delete': props<{ projectId: string; testCaseIds: string[] }>(),
    'Bulk Delete Success': props<{ projectId: string }>(),
    'Bulk Delete Failure': props<{ error: string }>(),
  },
});
