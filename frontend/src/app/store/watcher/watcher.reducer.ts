import { createReducer, on } from '@ngrx/store';
import { WatcherActions } from './watcher.actions';
import { initialWatcherState } from './watcher.state';

function watchKey(entityType: string, entityId: string): string {
  return `${entityType}:${entityId}`;
}

export const watcherReducer = createReducer(
  initialWatcherState,

  on(WatcherActions.watchEntitySuccess, (state, { entityType, entityId }) => ({
    ...state,
    watchStatus: { ...state.watchStatus, [watchKey(entityType, entityId)]: true },
  })),
  on(WatcherActions.watchEntityFailure, (state, { error }) => ({
    ...state,
    error,
  })),

  on(WatcherActions.unwatchEntitySuccess, (state, { entityType, entityId }) => ({
    ...state,
    watchStatus: { ...state.watchStatus, [watchKey(entityType, entityId)]: false },
    watchedItems: state.watchedItems.filter(
      (item) => !(item.entityType === entityType && item.entityId === entityId)
    ),
  })),
  on(WatcherActions.unwatchEntityFailure, (state, { error }) => ({
    ...state,
    error,
  })),

  on(WatcherActions.checkWatchStatusSuccess, (state, { entityType, entityId, watching }) => ({
    ...state,
    watchStatus: { ...state.watchStatus, [watchKey(entityType, entityId)]: watching },
  })),

  on(WatcherActions.loadWatchedItems, (state) => ({
    ...state,
    loading: true,
    error: null,
  })),
  on(WatcherActions.loadWatchedItemsSuccess, (state, { items }) => ({
    ...state,
    watchedItems: items,
    loading: false,
  })),
  on(WatcherActions.loadWatchedItemsFailure, (state, { error }) => ({
    ...state,
    loading: false,
    error,
  }))
);
