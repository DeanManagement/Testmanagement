export interface TestCaseFolder {
  id: string;
  name: string;
  parentId: string | null;
  sortOrder: number;
  testCaseCount: number;
  children: TestCaseFolder[];
  createdAt: string;
  updatedAt: string;
}

export interface CreateTestCaseFolderRequest {
  name: string;
  parentId?: string;
}

export interface UpdateTestCaseFolderRequest {
  name: string;
}

export interface FolderOrder {
  id: string;
  parentId: string | null;
  sortOrder: number;
}

export interface ReorderFoldersRequest {
  folders: FolderOrder[];
}

export interface MoveTestCasesRequest {
  testCaseIds: string[];
  targetFolderId: string | null;
}
