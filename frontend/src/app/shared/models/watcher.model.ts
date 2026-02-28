export type WatchableEntityType = 'TEST_PLAN' | 'TEST_RUN' | 'BUG_REPORT';

export interface WatchRequest {
  entityType: WatchableEntityType;
  entityId: string;
}

export interface WatchedItem {
  entityType: WatchableEntityType;
  entityId: string;
  name: string;
  status: string;
  projectId: string;
  watchedAt: string;
}
