import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { of } from 'rxjs';
import { catchError, map, mergeMap } from 'rxjs/operators';
import { WatcherActions } from './watcher.actions';
import { WatcherApiService } from '../../core/services/watcher-api.service';

@Injectable()
export class WatcherEffects {
  private readonly actions$ = inject(Actions);
  private readonly watcherApi = inject(WatcherApiService);

  watchEntity$ = createEffect(() =>
    this.actions$.pipe(
      ofType(WatcherActions.watchEntity),
      mergeMap(({ entityType, entityId }) =>
        this.watcherApi.watch(entityType, entityId).pipe(
          map(() => WatcherActions.watchEntitySuccess({ entityType, entityId })),
          catchError((error) => of(WatcherActions.watchEntityFailure({ error: error.message })))
        )
      )
    )
  );

  unwatchEntity$ = createEffect(() =>
    this.actions$.pipe(
      ofType(WatcherActions.unwatchEntity),
      mergeMap(({ entityType, entityId }) =>
        this.watcherApi.unwatch(entityType, entityId).pipe(
          map(() => WatcherActions.unwatchEntitySuccess({ entityType, entityId })),
          catchError((error) => of(WatcherActions.unwatchEntityFailure({ error: error.message })))
        )
      )
    )
  );

  checkWatchStatus$ = createEffect(() =>
    this.actions$.pipe(
      ofType(WatcherActions.checkWatchStatus),
      mergeMap(({ entityType, entityId }) =>
        this.watcherApi.isWatching(entityType, entityId).pipe(
          map(({ watching }) => WatcherActions.checkWatchStatusSuccess({ entityType, entityId, watching })),
          catchError((error) => of(WatcherActions.checkWatchStatusFailure({ error: error.message })))
        )
      )
    )
  );

  loadWatchedItems$ = createEffect(() =>
    this.actions$.pipe(
      ofType(WatcherActions.loadWatchedItems),
      mergeMap(() =>
        this.watcherApi.getMyWatchedItems().pipe(
          map((items) => WatcherActions.loadWatchedItemsSuccess({ items })),
          catchError((error) => of(WatcherActions.loadWatchedItemsFailure({ error: error.message })))
        )
      )
    )
  );
}
