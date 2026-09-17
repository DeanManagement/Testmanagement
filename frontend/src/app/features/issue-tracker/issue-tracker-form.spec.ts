import { describe, expect, it } from 'vitest';
import { baseUrlAfterProviderChange, needsAccountEmail } from './issue-tracker-form';

describe('needsAccountEmail', () => {
  describe('when the tracker is Jira Cloud', () => {
    it('should ask for the account email', () => {
      expect(needsAccountEmail('JIRA', 'https://acme.atlassian.net')).toBe(true);
      expect(needsAccountEmail('JIRA', ' https://ACME.atlassian.net/ ')).toBe(true);
    });
  });

  describe('when the tracker is Jira Data Center', () => {
    it('should not ask, because a personal access token authenticates on its own', () => {
      expect(needsAccountEmail('JIRA', 'https://jira.acme.example')).toBe(false);
    });
  });

  describe('when the host only looks like Atlassian', () => {
    it('should not ask', () => {
      expect(needsAccountEmail('JIRA', 'https://atlassian.net.evil.example')).toBe(false);
    });
  });

  describe('when the tracker is not Jira', () => {
    it('should not ask, whatever the host', () => {
      expect(needsAccountEmail('GITHUB', 'https://acme.atlassian.net')).toBe(false);
    });
  });

  describe('when the URL is still being typed', () => {
    it('should not ask and should not throw', () => {
      expect(needsAccountEmail('JIRA', 'https://')).toBe(false);
      expect(needsAccountEmail('JIRA', '')).toBe(false);
    });
  });
});

describe('baseUrlAfterProviderChange', () => {
  it('should fill in github.com when GitHub is chosen and the field is empty', () => {
    expect(baseUrlAfterProviderChange('GITHUB', '')).toBe('https://github.com');
  });

  it('should keep a URL the admin typed', () => {
    expect(baseUrlAfterProviderChange('GITHUB', 'https://git.corp.example')).toBe('https://git.corp.example');
  });

  it('should take its own suggestion back when the admin moves on to another tracker', () => {
    expect(baseUrlAfterProviderChange('JIRA', 'https://github.com')).toBe('');
  });

  it('should leave an empty field empty for a tracker without one obvious instance', () => {
    expect(baseUrlAfterProviderChange('GITLAB', '')).toBe('');
  });
});
