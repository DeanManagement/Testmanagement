import { createActionGroup, emptyProps, props } from '@ngrx/store';
import { WatchableEntityType, WatchedItem } from '../../shared/models/watcher.model';

export const WatcherActions = createActionGroup({
  source: 'Watchers',
  events: {
    'Watch Entity': props<{ entityType: WatchableEntityType; entityId: string }>(),
    'Watch Entity Success': props<{ entityType: WatchableEntityType; entityId: string }>(),
    'Watch Entity Failure': props<{ error: string }>(),

    'Unwatch Entity': props<{ entityType: WatchableEntityType; entityId: string }>(),
    'Unwatch Entity Success': props<{ entityType: WatchableEntityType; entityId: string }>(),
    'Unwatch Entity Failure': props<{ error: string }>(),

    'Check Watch Status': props<{ entityType: WatchableEntityType; entityId: string }>(),
    'Check Watch Status Success': props<{ entityType: WatchableEntityType; entityId: string; watching: boolean }>(),
    'Check Watch Status Failure': props<{ error: string }>(),

    'Load Watched Items': emptyProps(),
    'Load Watched Items Success': props<{ items: WatchedItem[] }>(),
    'Load Watched Items Failure': props<{ error: string }>(),
  },
});
