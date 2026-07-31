export type IssueTrackerProviderType = 'GITLAB' | 'FORGEJO' | 'GITHUB' | 'JIRA' | 'LINEAR';

export type IssueState = 'OPEN' | 'CLOSED' | 'UNKNOWN';

export interface IssueTrackerConfig {
  id: string;
  provider: IssueTrackerProviderType;
  baseUrl: string;
  projectRef: string;
  active: boolean;
  /** The token itself is never returned by the API; this only says whether one is stored. */
  tokenSet: boolean;
  lastError: string | null;
  lastErrorAt: string | null;
  updatedAt: string;
}

export interface SaveIssueTrackerConfigRequest {
  provider: IssueTrackerProviderType;
  baseUrl: string;
  projectRef: string;
  /** Omit to keep the stored token — the backend treats absent as "unchanged". */
  apiToken?: string;
  active?: boolean;
}

export interface IssueTrackerStatus {
  configured: boolean;
  provider: IssueTrackerProviderType | null;
}

export interface IssueSearchResult {
  externalId: string;
  url: string;
  title: string | null;
  state: IssueState;
}

export interface IssueLink {
  id: string;
  testResultId: string;
  provider: IssueTrackerProviderType;
  externalId: string;
  url: string;
  title: string | null;
  state: IssueState;
  stateCheckedAt: string | null;
}

export interface CreateIssueLinkRequest {
  externalId?: string;
  create?: boolean;
  title?: string;
  body?: string;
}

/** What the project reference means for each provider, shown as form hint text. */
export const PROJECT_REF_HINT: Record<IssueTrackerProviderType, string> = {
  GITLAB: 'group/project or a numeric project id',
  FORGEJO: 'owner/repository',
  GITHUB: 'owner/repository',
  JIRA: 'project key',
  LINEAR: 'team key',
};
