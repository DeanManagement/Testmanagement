import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { of } from 'rxjs';
import { catchError, map, mergeMap } from 'rxjs/operators';
import { ApiKeyApiService } from '../../core/services/api-key-api.service';
import { ApiKeyActions } from './api-key.actions';

@Injectable()
export class ApiKeyEffects {
  private readonly actions$ = inject(Actions);
  private readonly apiKeyApi = inject(ApiKeyApiService);

  loadApiKeys$ = createEffect(() =>
    this.actions$.pipe(
      ofType(ApiKeyActions.loadApiKeys),
      mergeMap(() =>
        this.apiKeyApi.getAll().pipe(
          map((apiKeys) => ApiKeyActions.loadApiKeysSuccess({ apiKeys })),
          catchError((error) =>
            of(ApiKeyActions.loadApiKeysFailure({ error: error.message }))
          )
        )
      )
    )
  );

  createApiKey$ = createEffect(() =>
    this.actions$.pipe(
      ofType(ApiKeyActions.createApiKey),
      mergeMap(({ request }) =>
        this.apiKeyApi.create(request).pipe(
          map((created) => ApiKeyActions.createApiKeySuccess({ created })),
          catchError((error) =>
            of(ApiKeyActions.createApiKeyFailure({ error: error.message }))
          )
        )
      )
    )
  );

  revokeApiKey$ = createEffect(() =>
    this.actions$.pipe(
      ofType(ApiKeyActions.revokeApiKey),
      mergeMap(({ id }) =>
        this.apiKeyApi.revoke(id).pipe(
          map(() => ApiKeyActions.revokeApiKeySuccess({ id })),
          catchError((error) =>
            of(ApiKeyActions.revokeApiKeyFailure({ error: error.message }))
          )
        )
      )
    )
  );
}
