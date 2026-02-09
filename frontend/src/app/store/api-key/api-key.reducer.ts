import { createReducer, on } from '@ngrx/store';
import { ApiKeyActions } from './api-key.actions';
import { apiKeyAdapter, initialApiKeyState } from './api-key.state';

export const apiKeyReducer = createReducer(
  initialApiKeyState,

  on(ApiKeyActions.loadApiKeys, (state) => ({
    ...state,
    loading: true,
    error: null,
  })),

  on(ApiKeyActions.loadApiKeysSuccess, (state, { apiKeys }) =>
    apiKeyAdapter.setAll(apiKeys, { ...state, loading: false })
  ),

  on(ApiKeyActions.loadApiKeysFailure, (state, { error }) => ({
    ...state,
    loading: false,
    error,
  })),

  on(ApiKeyActions.createApiKeySuccess, (state, { created }) =>
    apiKeyAdapter.addOne(
      {
        id: created.id,
        name: created.name,
        keyPrefix: created.keyPrefix,
        revoked: false,
        lastUsedAt: null,
        createdAt: created.createdAt,
      },
      state
    )
  ),

  on(ApiKeyActions.createApiKeyFailure, (state, { error }) => ({
    ...state,
    error,
  })),

  on(ApiKeyActions.revokeApiKeySuccess, (state, { id }) =>
    apiKeyAdapter.updateOne({ id, changes: { revoked: true } }, state)
  ),

  on(ApiKeyActions.revokeApiKeyFailure, (state, { error }) => ({
    ...state,
    error,
  }))
);
