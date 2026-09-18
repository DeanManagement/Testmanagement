import { WebhookEventType, WebhookFormat } from '../../shared/models/webhook.model';

/** Where each vendor explains how to create an incoming webhook. */
export const FORMAT_SETUP_DOCS: Record<WebhookFormat, string | null> = {
  GENERIC: null,
  SLACK: 'https://api.slack.com/messaging/webhooks',
  TEAMS: 'https://support.microsoft.com/office/create-incoming-webhooks-with-workflows-for-microsoft-teams-8ae491c7-0394-4861-ba59-055e33f75498',
};

/** Pre-ticked when a new webhook is switched to a chat format. */
const CHAT_DEFAULT_EVENTS: WebhookEventType[] = ['RUN_COMPLETED', 'RUN_FAILED', 'BUG_REPORT_CREATED'];

export function isChatFormat(format: WebhookFormat): boolean {
  return format !== 'GENERIC';
}

/**
 * One TEST_FAILED per failed result would flood a channel, so chat webhooks get run summaries
 * only. Mirrors the server, which rejects the combination with a 400.
 */
export function isEventAllowed(format: WebhookFormat, event: WebhookEventType): boolean {
  return !(isChatFormat(format) && event === 'TEST_FAILED');
}

/**
 * The event selection after the format changes: disallowed events are dropped, and a new webhook
 * with nothing ticked yet gets the usual chat events. An existing selection is otherwise kept.
 */
export function eventsAfterFormatChange(
  format: WebhookFormat,
  events: ReadonlySet<WebhookEventType>,
  isNew: boolean,
): Set<WebhookEventType> {
  const kept = new Set([...events].filter((event) => isEventAllowed(format, event)));
  if (isNew && isChatFormat(format) && kept.size === 0) {
    return new Set(CHAT_DEFAULT_EVENTS);
  }
  return kept;
}

export interface WebhookFormState {
  format: WebhookFormat;
  isNew: boolean;
  /** False while an existing chat webhook keeps its stored (masked) URL. */
  editingUrl: boolean;
  url: string;
  secret: string;
  events: ReadonlySet<WebhookEventType>;
}

export function isWebhookFormValid(state: WebhookFormState): boolean {
  const urlOk = !state.editingUrl || state.url.trim().length > 0;
  const secretOk = !state.isNew || isChatFormat(state.format) || state.secret.trim().length > 0;
  const eventsOk = state.events.size > 0 && [...state.events].every((event) => isEventAllowed(state.format, event));
  return urlOk && secretOk && eventsOk;
}
