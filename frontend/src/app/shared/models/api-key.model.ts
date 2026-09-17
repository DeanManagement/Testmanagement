/**
 * PRD-025 §3.2: the role a key holds on its project, via the service account it authenticates as.
 * ADMIN is not offered — a key must never be able to manage members or delete a project.
 */
export type ApiKeyRole = 'VIEWER' | 'TESTER';

/**
 * PRD-027 §9: the units MCP access is granted in. CORE (get_project) is always granted by the
 * server and is deliberately absent here — it can be neither selected nor withheld.
 */
export type McpToolGroup = 'AUTHORING' | 'EXECUTION' | 'REPORTING' | 'PIPELINES' | 'ISSUE_TRACKER';

export const MCP_TOOL_GROUPS: readonly McpToolGroup[] = [
  'AUTHORING',
  'EXECUTION',
  'REPORTING',
  'PIPELINES',
  'ISSUE_TRACKER',
];

export interface ApiKey {
  id: string;
  name: string;
  keyPrefix: string;
  revoked: boolean;
  lastUsedAt: string | null;
  createdAt: string;
  /** PRD-021: null for legacy/global keys. */
  projectId: string | null;
  projectName: string | null;
  role: ApiKeyRole;
  /** Null while the key still carries the secret it was issued with. */
  rotatedAt: string | null;
  /** Null when the key may use every MCP tool group. */
  mcpToolGroups: McpToolGroup[] | null;
}

export interface ApiKeyCreated {
  id: string;
  name: string;
  keyPrefix: string;
  rawKey: string;
  createdAt: string;
  projectId: string;
  projectName: string;
  role: ApiKeyRole;
  /** Null when the key may use every MCP tool group. */
  mcpToolGroups: McpToolGroup[] | null;
}

export interface CreateApiKeyRequest {
  name: string;
  projectId: string;
  role: ApiKeyRole;
  /** Omitted or empty means every MCP tool group. */
  mcpToolGroups?: McpToolGroup[];
}
