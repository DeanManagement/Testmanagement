import { createFeatureSelector, createSelector } from '@ngrx/store';
import { TestCaseFolderState } from './test-case-folder.state';

export const selectTestCaseFolderState = createFeatureSelector<TestCaseFolderState>('testCaseFolders');

export const selectFolderTree = createSelector(
  selectTestCaseFolderState,
  (state) => state.folders
);

export const selectFoldersLoading = createSelector(
  selectTestCaseFolderState,
  (state) => state.loading
);

export const selectSelectedFolderId = createSelector(
  selectTestCaseFolderState,
  (state) => state.selectedFolderId
);
