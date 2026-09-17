import { ApiKeyCreated } from '../../shared/models/api-key.model';
import { ApiKeyActions } from './api-key.actions';
import { apiKeyReducer } from './api-key.reducer';
import { initialApiKeyState } from './api-key.state';

describe('apiKeyReducer', () => {
  describe('createApiKeySuccess', () => {
    const created: ApiKeyCreated = {
      id: 'key-1',
      name: 'Execution agent',
      keyPrefix: 'tm_abc12',
      rawKey: 'tm_abc123',
      createdAt: '2026-09-17T10:00:00Z',
      projectId: 'project-1',
      projectName: 'Demo',
      role: 'TESTER',
      mcpToolGroups: ['EXECUTION'],
    };

    it('should list the new key with the tool groups it was restricted to', () => {
      const state = apiKeyReducer(initialApiKeyState, ApiKeyActions.createApiKeySuccess({ created }));

      expect(state.entities['key-1']?.mcpToolGroups).toEqual(['EXECUTION']);
    });

    it('should list an unrestricted key with no tool groups', () => {
      const unrestricted = { ...created, mcpToolGroups: null };

      const state = apiKeyReducer(
        initialApiKeyState,
        ApiKeyActions.createApiKeySuccess({ created: unrestricted })
      );

      expect(state.entities['key-1']?.mcpToolGroups).toBeNull();
    });
  });
});
