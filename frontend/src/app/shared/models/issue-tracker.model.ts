export type IssueTrackerProviderType = 'GITLAB' | 'FORGEJO' | 'GITHUB' | 'JIRA' | 'LINEAR' | 'AZURE_DEVOPS';

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
  /** Jira Cloud only (PRD-029): the account the token belongs to. Not a secret, so it is returned. */
  authUsername: string | null;
  /** Azure DevOps only (PRD-026): null means 7.1. */
  apiVersion: string | null;
  /** Azure DevOps only: the work item type bugs are filed as; null means Bug. */
  workItemType: string | null;
}

export interface SaveIssueTrackerConfigRequest {
  provider: IssueTrackerProviderType;
  baseUrl: string;
  projectRef: string;
  /** Omit to keep the stored token — the backend treats absent as "unchanged". */
  apiToken?: string;
  active?: boolean;
  /** Required by the server for Jira Cloud, ignored for everything else. */
  authUsername?: string;
  apiVersion?: string | null;
  workItemType?: string | null;
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
  JIRA: 'project key, optionally KEY:IssueType (default issue type: Bug)',
  LINEAR: 'team key',
  AZURE_DEVOPS: 'Azure DevOps project, e.g. Payments',
};
