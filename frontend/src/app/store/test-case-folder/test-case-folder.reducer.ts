import { createReducer, on } from '@ngrx/store';
import { TestCaseFolderActions } from './test-case-folder.actions';
import { initialTestCaseFolderState } from './test-case-folder.state';

export const testCaseFolderReducer = createReducer(
  initialTestCaseFolderState,

  on(TestCaseFolderActions.loadFolders, (state) => ({
    ...state,
    loading: true,
    error: null,
  })),

  on(TestCaseFolderActions.loadFoldersSuccess, (state, { folders }) => ({
    ...state,
    folders,
    loading: false,
  })),

  on(TestCaseFolderActions.loadFoldersFailure, (state, { error }) => ({
    ...state,
    loading: false,
    error,
  })),

  on(TestCaseFolderActions.createFolderFailure, (state, { error }) => ({
    ...state,
    error,
  })),

  on(TestCaseFolderActions.updateFolderFailure, (state, { error }) => ({
    ...state,
    error,
  })),

  on(TestCaseFolderActions.deleteFolderSuccess, (state, { projectId }) => {
    const deletedFolderId = state.selectedFolderId;
    return {
      ...state,
      selectedFolderId: null,
    };
  }),

  on(TestCaseFolderActions.deleteFolderFailure, (state, { error }) => ({
    ...state,
    error,
  })),

  on(TestCaseFolderActions.reorderFoldersSuccess, (state, { folders }) => ({
    ...state,
    folders,
  })),

  on(TestCaseFolderActions.reorderFoldersFailure, (state, { error }) => ({
    ...state,
    error,
  })),

  on(TestCaseFolderActions.moveTestCasesFailure, (state, { error }) => ({
    ...state,
    error,
  })),

  on(TestCaseFolderActions.selectFolder, (state, { folderId }) => ({
    ...state,
    selectedFolderId: folderId,
  }))
);
