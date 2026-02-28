import { createFeatureSelector, createSelector } from '@ngrx/store';
import { WatcherState } from './watcher.state';
import { WatchableEntityType } from '../../shared/models/watcher.model';

export const selectWatcherState = createFeatureSelector<WatcherState>('watchers');

export const selectWatchedItems = createSelector(
  selectWatcherState,
  (state) => state.watchedItems
);

export const selectWatchedItemsLoading = createSelector(
  selectWatcherState,
  (state) => state.loading
);

export const selectIsWatching = (entityType: WatchableEntityType, entityId: string) =>
  createSelector(
    selectWatcherState,
    (state) => state.watchStatus[`${entityType}:${entityId}`] ?? false
  );
