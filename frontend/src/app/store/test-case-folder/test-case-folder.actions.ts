import { createActionGroup, props } from '@ngrx/store';
import {
  TestCaseFolder,
  CreateTestCaseFolderRequest,
  UpdateTestCaseFolderRequest,
  ReorderFoldersRequest,
  MoveTestCasesRequest,
} from '../../shared/models/test-case-folder.model';

export const TestCaseFolderActions = createActionGroup({
  source: 'TestCaseFolders',
  events: {
    'Load Folders': props<{ projectId: string }>(),
    'Load Folders Success': props<{ folders: TestCaseFolder[] }>(),
    'Load Folders Failure': props<{ error: string }>(),
    'Create Folder': props<{ projectId: string; request: CreateTestCaseFolderRequest }>(),
    'Create Folder Success': props<{ projectId: string }>(),
    'Create Folder Failure': props<{ error: string }>(),
    'Update Folder': props<{ projectId: string; folderId: string; request: UpdateTestCaseFolderRequest }>(),
    'Update Folder Success': props<{ projectId: string }>(),
    'Update Folder Failure': props<{ error: string }>(),
    'Delete Folder': props<{ projectId: string; folderId: string }>(),
    'Delete Folder Success': props<{ projectId: string }>(),
    'Delete Folder Failure': props<{ error: string }>(),
    'Reorder Folders': props<{ projectId: string; request: ReorderFoldersRequest }>(),
    'Reorder Folders Success': props<{ folders: TestCaseFolder[] }>(),
    'Reorder Folders Failure': props<{ error: string }>(),
    'Move Test Cases': props<{ projectId: string; request: MoveTestCasesRequest }>(),
    'Move Test Cases Success': props<{ projectId: string }>(),
    'Move Test Cases Failure': props<{ error: string }>(),
    'Select Folder': props<{ folderId: string | null }>(),
  },
});
