import { EntityState, createEntityAdapter } from '@ngrx/entity';
import { ApiKey } from '../../shared/models/api-key.model';

export const apiKeyAdapter = createEntityAdapter<ApiKey>();

export interface ApiKeyState extends EntityState<ApiKey> {
  loading: boolean;
  error: string | null;
}

export const initialApiKeyState: ApiKeyState = apiKeyAdapter.getInitialState({
  loading: false,
  error: null,
});
