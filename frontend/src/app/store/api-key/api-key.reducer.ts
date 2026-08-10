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
        projectId: created.projectId,
        projectName: created.projectName,
        role: created.role,
        rotatedAt: null,
      },
      state
    )
  ),

  on(ApiKeyActions.createApiKeyFailure, (state, { error }) => ({
    ...state,
    error,
  })),

  // Mirrors what the server did: new prefix, last-used cleared so its reappearance means the new
  // secret has been picked up.
  on(ApiKeyActions.rotateApiKeySuccess, (state, { created }) =>
    apiKeyAdapter.updateOne(
      {
        id: created.id,
        changes: {
          keyPrefix: created.keyPrefix,
          lastUsedAt: null,
          rotatedAt: new Date().toISOString(),
        },
      },
      state
    )
  ),

  on(ApiKeyActions.rotateApiKeyFailure, (state, { error }) => ({
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
