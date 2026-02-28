import { WatchedItem } from '../../shared/models/watcher.model';

export interface WatcherState {
  watchedItems: WatchedItem[];
  loading: boolean;
  error: string | null;
  watchStatus: Record<string, boolean>;
}

export const initialWatcherState: WatcherState = {
  watchedItems: [],
  loading: false,
  error: null,
  watchStatus: {},
};
