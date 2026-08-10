import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { catchError, map, mergeMap, tap } from 'rxjs/operators';
import { ApiKeyApiService } from '../../core/services/api-key-api.service';
import { ApiKeyActions } from './api-key.actions';

@Injectable()
export class ApiKeyEffects {
  private readonly actions$ = inject(Actions);
  private readonly apiKeyApi = inject(ApiKeyApiService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly translate = inject(TranslateService);

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

  createApiKeySnackbar$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(ApiKeyActions.createApiKeySuccess),
        tap(() => this.snackBar.open(this.translate.instant('common.savedSuccessfully'), this.translate.instant('common.close'), { duration: 3000 }))
      ),
    { dispatch: false }
  );

  rotateApiKey$ = createEffect(() =>
    this.actions$.pipe(
      ofType(ApiKeyActions.rotateApiKey),
      mergeMap(({ id }) =>
        this.apiKeyApi.rotate(id).pipe(
          map((created) => ApiKeyActions.rotateApiKeySuccess({ created })),
          catchError((error) =>
            of(ApiKeyActions.rotateApiKeyFailure({ error: error.message }))
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

  revokeApiKeySnackbar$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(ApiKeyActions.revokeApiKeySuccess),
        tap(() => this.snackBar.open(this.translate.instant('common.deletedSuccessfully'), this.translate.instant('common.close'), { duration: 3000 }))
      ),
    { dispatch: false }
  );
}
