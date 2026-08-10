import { createActionGroup, emptyProps, props } from '@ngrx/store';
import { ApiKey, ApiKeyCreated, CreateApiKeyRequest } from '../../shared/models/api-key.model';

export const ApiKeyActions = createActionGroup({
  source: 'ApiKeys',
  events: {
    'Load Api Keys': emptyProps(),
    'Load Api Keys Success': props<{ apiKeys: ApiKey[] }>(),
    'Load Api Keys Failure': props<{ error: string }>(),
    'Create Api Key': props<{ request: CreateApiKeyRequest }>(),
    'Create Api Key Success': props<{ created: ApiKeyCreated }>(),
    'Create Api Key Failure': props<{ error: string }>(),
    'Rotate Api Key': props<{ id: string }>(),
    'Rotate Api Key Success': props<{ created: ApiKeyCreated }>(),
    'Rotate Api Key Failure': props<{ error: string }>(),
    'Revoke Api Key': props<{ id: string }>(),
    'Revoke Api Key Success': props<{ id: string }>(),
    'Revoke Api Key Failure': props<{ error: string }>(),
  },
});
