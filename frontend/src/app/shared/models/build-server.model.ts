/** PRD-024: global build servers, workflow definitions, and triggered pipeline runs. */

export type BuildServerProviderType =
  | 'GITLAB_CI'
  | 'GITHUB_ACTIONS'
  | 'FORGEJO_ACTIONS'
  | 'WOODPECKER'
  | 'JENKINS'
  | 'AZURE_DEVOPS';

export type PipelineRunStatus =
  | 'TRIGGERED'
  | 'PENDING'
  | 'RUNNING'
  | 'SUCCESS'
  | 'FAILED'
  | 'CANCELLED'
  | 'TIMED_OUT'
  | 'ERROR';

export const PIPELINE_RUN_ACTIVE_STATUSES: PipelineRunStatus[] = ['TRIGGERED', 'PENDING', 'RUNNING'];

/** Placeholder text for the provider-specific repository/job reference. */
export const REPO_REF_HINT: Record<BuildServerProviderType, string> = {
  GITLAB_CI: 'group/project or numeric project id',
  GITHUB_ACTIONS: 'owner/repo',
  FORGEJO_ACTIONS: 'owner/repo',
  WOODPECKER: 'numeric repository id (use discovery)',
  JENKINS: 'folder/subfolder/jobname',
  AZURE_DEVOPS: 'organization/project',
};

export interface BuildServerConfig {
  id: string;
  name: string;
  provider: BuildServerProviderType;
  baseUrl: string;
  active: boolean;
  tokenSet: boolean;
  lastError: string | null;
  lastErrorAt: string | null;
  updatedAt: string;
}

export interface SaveBuildServerConfigRequest {
  name: string;
  provider: BuildServerProviderType;
  baseUrl: string;
  apiToken?: string;
  active?: boolean;
}

export interface BuildWorkflow {
  id: string;
  buildServerConfigId: string;
  name: string;
  repoRef: string;
  workflowRef: string | null;
  defaultRef: string | null;
  defaultParameters: Record<string, string>;
  active: boolean;
  projectIds: string[];
  updatedAt: string;
}

export interface SaveBuildWorkflowRequest {
  name: string;
  repoRef: string;
  workflowRef?: string | null;
  defaultRef?: string | null;
  defaultParameters?: Record<string, string>;
  active?: boolean;
}

export interface DiscoveredWorkflow {
  name: string;
  repoRef: string;
  workflowRef: string | null;
  defaultRef: string | null;
}

export interface DiscoverWorkflowsResponse {
  supported: boolean;
  workflows: DiscoveredWorkflow[];
}

/** A workflow as a project member sees it: enough to trigger, no server internals. */
export interface ProjectWorkflow {
  id: string;
  name: string;
  serverName: string;
  provider: BuildServerProviderType;
  defaultRef: string | null;
  defaultParameters: Record<string, string>;
}

export interface TriggerPipelineRequest {
  ref?: string | null;
  parameters?: Record<string, string>;
}

export interface PipelineRun {
  id: string;
  workflowId: string | null;
  workflowName: string;
  status: PipelineRunStatus;
  externalRunId: string | null;
  externalUrl: string | null;
  triggeredRef: string | null;
  parameters: Record<string, string>;
  testRunId: string | null;
  testRunKey: string | null;
  errorMessage: string | null;
  createdAt: string;
  finishedAt: string | null;
}

export function isActivePipelineRun(run: PipelineRun): boolean {
  return PIPELINE_RUN_ACTIVE_STATUSES.includes(run.status);
}
