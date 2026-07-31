import { EntityState, createEntityAdapter } from '@ngrx/entity';
import { TestSuite } from '../../shared/models/test-suite.model';
import { EMPTY_PAGE_METADATA, PageMetadata } from '../../shared/models/page.model';

export const testSuiteAdapter = createEntityAdapter<TestSuite>({ sortComparer: false });

export interface TestSuiteState extends EntityState<TestSuite> {
  loading: boolean;
  error: string | null;
  projectId: string | null;
  page: PageMetadata;
}

export const initialTestSuiteState: TestSuiteState = testSuiteAdapter.getInitialState({
  loading: false,
  error: null,
  projectId: null,
  page: EMPTY_PAGE_METADATA,
});
