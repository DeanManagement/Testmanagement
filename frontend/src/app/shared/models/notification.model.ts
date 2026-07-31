export type NotificationAction =
  | 'CREATED' | 'UPDATED' | 'DELETED' | 'STATUS_CHANGED'
  | 'COMPLETED' | 'REOPENED' | 'CLONED' | 'MOVED';

export type WatchableEntityType = 'TEST_PLAN' | 'TEST_RUN' | 'BUG_REPORT';

export interface AppNotification {
  id: string;
  projectId: string;
  entityType: WatchableEntityType;
  entityId: string;
  action: NotificationAction;
  entityName: string | null;
  actorName: string | null;
  read: boolean;
  createdAt: string;
}

export interface NotificationPreference {
  action: NotificationAction;
  inApp: boolean;
  email: boolean;
}

export const NOTIFICATION_ACTIONS: NotificationAction[] = [
  'CREATED', 'UPDATED', 'DELETED', 'STATUS_CHANGED', 'COMPLETED', 'REOPENED', 'CLONED', 'MOVED',
];
