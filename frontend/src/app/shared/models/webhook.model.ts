export type WebhookEventType =
  | 'RUN_STARTED'
  | 'RUN_COMPLETED'
  | 'RUN_FAILED'
  | 'TEST_FAILED'
  | 'PLAN_COMPLETED'
  | 'BUG_REPORT_CREATED';

export const ALL_WEBHOOK_EVENTS: WebhookEventType[] = [
  'RUN_STARTED',
  'RUN_COMPLETED',
  'RUN_FAILED',
  'TEST_FAILED',
  'PLAN_COMPLETED',
  'BUG_REPORT_CREATED',
];

export interface Webhook {
  id: string;
  url: string;
  events: WebhookEventType[];
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateWebhookRequest {
  url: string;
  secret: string;
  events: WebhookEventType[];
  active: boolean;
}

export interface UpdateWebhookRequest {
  url: string;
  secret?: string;
  events: WebhookEventType[];
  active: boolean;
}

export interface WebhookDelivery {
  id: string;
  event: WebhookEventType;
  responseStatus: number | null;
  attempt: number;
  success: boolean | null;
  error: string | null;
  createdAt: string;
}
