import { createFeatureSelector, createSelector } from '@ngrx/store';
import { ApiKeyState, apiKeyAdapter } from './api-key.state';

export const selectApiKeyState = createFeatureSelector<ApiKeyState>('apiKeys');

const { selectAll } = apiKeyAdapter.getSelectors();

export const selectAllApiKeys = createSelector(selectApiKeyState, selectAll);

export const selectApiKeysLoading = createSelector(
  selectApiKeyState,
  (state) => state.loading
);
