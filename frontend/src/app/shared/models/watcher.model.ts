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
  /** TM47: a bug's own status name; null for plans, runs and bugs without one. */
  statusName?: string | null;
  projectId: string;
  watchedAt: string;
}
