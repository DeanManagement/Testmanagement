import { IssueTrackerProviderType } from '../../shared/models/issue-tracker.model';

const JIRA_CLOUD_HOST_SUFFIX = '.atlassian.net';

/** What an admin would paste for each tracker; used as the placeholder and, for GitHub, prefilled. */
export const BASE_URL_EXAMPLE: Record<IssueTrackerProviderType, string> = {
  GITLAB: 'https://gitlab.com',
  FORGEJO: 'https://codeberg.org',
  GITHUB: 'https://github.com',
  JIRA: 'https://your-site.atlassian.net',
  LINEAR: 'https://linear.app',
  // The organization (or, on Server, the collection) belongs in the URL, not in the project field.
  AZURE_DEVOPS: 'https://dev.azure.com/your-organization',
};

/**
 * Jira Cloud authenticates with the account email plus the API token; every other tracker,
 * Jira Data Center included, takes the token alone (PRD-029 §3.1). Mirrors the server, which
 * requires the email under exactly this condition — the parsed host must end in `.atlassian.net`,
 * so `atlassian.net.evil.example` does not count.
 */
export function needsAccountEmail(provider: IssueTrackerProviderType, baseUrl: string): boolean {
  if (provider !== 'JIRA') {
    return false;
  }
  try {
    return new URL(baseUrl.trim()).hostname.toLowerCase().endsWith(JIRA_CLOUD_HOST_SUFFIX);
  } catch {
    // Still being typed, or not a URL at all.
    return false;
  }
}

/**
 * The base URL to show after the provider changes. GitHub has one public instance nearly everyone
 * means, so it is filled in; a URL the admin typed, or one that belongs to the saved config, is
 * never overwritten — only an empty field or a value this function itself suggested.
 */
export function baseUrlAfterProviderChange(provider: IssueTrackerProviderType, currentBaseUrl: string): string {
  const current = currentBaseUrl.trim();
  const isOurSuggestion = current === BASE_URL_EXAMPLE.GITHUB;
  if (current !== '' && !isOurSuggestion) {
    return currentBaseUrl;
  }
  return provider === 'GITHUB' ? BASE_URL_EXAMPLE.GITHUB : '';
}
