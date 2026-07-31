import { EntityState, createEntityAdapter } from '@ngrx/entity';
import { TestCase } from '../../shared/models/test-case.model';
import { EMPTY_PAGE_METADATA, PageMetadata } from '../../shared/models/page.model';

export const testCaseAdapter = createEntityAdapter<TestCase>({
  // Preserve the server-provided ordering (sort is applied server-side).
  sortComparer: false,
});

export interface TestCaseState extends EntityState<TestCase> {
  loading: boolean;
  error: string | null;
  projectId: string | null;
  selectedIds: string[];
  page: PageMetadata;
}

export const initialTestCaseState: TestCaseState = testCaseAdapter.getInitialState({
  loading: false,
  error: null,
  projectId: null,
  selectedIds: [],
  page: EMPTY_PAGE_METADATA,
});
