import { EntityState, createEntityAdapter } from '@ngrx/entity';
import { TestRun } from '../../shared/models/test-run.model';
import { EMPTY_PAGE_METADATA, PageMetadata } from '../../shared/models/page.model';

export const testRunAdapter = createEntityAdapter<TestRun>({ sortComparer: false });

export interface TestRunState extends EntityState<TestRun> {
  loading: boolean;
  error: string | null;
  projectId: string | null;
  page: PageMetadata;
  myActiveTestRuns: TestRun[];
  myActiveTestRunsLoading: boolean;
  myCompletedTestRuns: TestRun[];
  myCompletedTestRunsLoading: boolean;
  myCompletedTestRunsLoaded: boolean;
}

export const initialTestRunState: TestRunState = testRunAdapter.getInitialState({
  loading: false,
  error: null,
  projectId: null,
  page: EMPTY_PAGE_METADATA,
  myActiveTestRuns: [],
  myActiveTestRunsLoading: false,
  myCompletedTestRuns: [],
  myCompletedTestRunsLoading: false,
  myCompletedTestRunsLoaded: false,
});
