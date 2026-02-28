import { TestCaseFolder } from '../../shared/models/test-case-folder.model';

export interface TestCaseFolderState {
  folders: TestCaseFolder[];
  loading: boolean;
  error: string | null;
  selectedFolderId: string | null;
}

export const initialTestCaseFolderState: TestCaseFolderState = {
  folders: [],
  loading: false,
  error: null,
  selectedFolderId: null,
};
