import { describe, expect, it } from 'vitest';
import { eventsAfterFormatChange, isEventAllowed, isWebhookFormValid, WebhookFormState } from './webhook-form';

describe('isEventAllowed', () => {
  it('should refuse TEST_FAILED for chat formats', () => {
    expect(isEventAllowed('SLACK', 'TEST_FAILED')).toBe(false);
    expect(isEventAllowed('TEAMS', 'TEST_FAILED')).toBe(false);
  });

  it('should allow every event for generic webhooks', () => {
    expect(isEventAllowed('GENERIC', 'TEST_FAILED')).toBe(true);
  });
});

describe('eventsAfterFormatChange', () => {
  describe('when a new webhook switches to a chat format with nothing ticked', () => {
    it('should pre-tick the run and bug events', () => {
      expect([...eventsAfterFormatChange('SLACK', new Set(), true)])
        .toEqual(['RUN_COMPLETED', 'RUN_FAILED', 'BUG_REPORT_CREATED']);
    });
  });

  describe('when switching to a chat format with TEST_FAILED ticked', () => {
    it('should drop TEST_FAILED and keep the rest', () => {
      expect([...eventsAfterFormatChange('TEAMS', new Set(['TEST_FAILED', 'RUN_STARTED'] as const), false)])
        .toEqual(['RUN_STARTED']);
    });
  });

  describe('when an existing webhook ends up with nothing ticked', () => {
    it('should not invent a selection', () => {
      expect(eventsAfterFormatChange('SLACK', new Set(['TEST_FAILED'] as const), false).size).toBe(0);
    });
  });
});

describe('isWebhookFormValid', () => {
  const base: WebhookFormState = {
    format: 'GENERIC',
    isNew: true,
    editingUrl: true,
    url: 'https://example.com/hook',
    secret: 's3cret',
    events: new Set(['RUN_COMPLETED']),
  };

  it('should require a secret for a new generic webhook', () => {
    expect(isWebhookFormValid({ ...base, secret: ' ' })).toBe(false);
  });

  it('should not require a secret for a new chat webhook', () => {
    expect(isWebhookFormValid({ ...base, format: 'SLACK', secret: '' })).toBe(true);
  });

  it('should not require a URL while an existing chat webhook keeps its stored one', () => {
    expect(isWebhookFormValid({ ...base, format: 'SLACK', isNew: false, editingUrl: false, url: '' })).toBe(true);
  });

  it('should require a URL when one is being entered', () => {
    expect(isWebhookFormValid({ ...base, url: '' })).toBe(false);
  });

  it('should reject a chat webhook subscribed to TEST_FAILED', () => {
    expect(isWebhookFormValid({ ...base, format: 'TEAMS', events: new Set(['TEST_FAILED']) })).toBe(false);
  });
});
