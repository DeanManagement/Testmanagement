import { createActionGroup, props } from '@ngrx/store';
import { CreateTestSuiteRequest, TestSuite, TestSuiteQuery, UpdateTestSuiteRequest } from '../../shared/models/test-suite.model';
import { PageMetadata } from '../../shared/models/page.model';

export const TestSuiteActions = createActionGroup({
  source: 'TestSuites',
  events: {
    'Load Test Suites': props<{ projectId: string; query?: TestSuiteQuery }>(),
    'Load Test Suites Success': props<{ testSuites: TestSuite[]; page: PageMetadata }>(),
    'Load Test Suites Failure': props<{ error: string }>(),
    'Load Test Suite': props<{ projectId: string; id: string }>(),
    'Load Test Suite Success': props<{ testSuite: TestSuite }>(),
    'Load Test Suite Failure': props<{ error: string }>(),
    'Create Test Suite': props<{ projectId: string; request: CreateTestSuiteRequest }>(),
    'Create Test Suite Success': props<{ testSuite: TestSuite }>(),
    'Create Test Suite Failure': props<{ error: string }>(),
    'Update Test Suite': props<{ projectId: string; id: string; request: UpdateTestSuiteRequest }>(),
    'Update Test Suite Success': props<{ testSuite: TestSuite }>(),
    'Update Test Suite Failure': props<{ error: string }>(),
    'Delete Test Suite': props<{ projectId: string; id: string }>(),
    'Delete Test Suite Success': props<{ id: string }>(),
    'Delete Test Suite Failure': props<{ error: string }>(),
  },
});
