/**
 * PRD-025 §3.2: the role a key holds on its project, via the service account it authenticates as.
 * ADMIN is not offered — a key must never be able to manage members or delete a project.
 */
export type ApiKeyRole = 'VIEWER' | 'TESTER';

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
}

export interface CreateApiKeyRequest {
  name: string;
  projectId: string;
  role: ApiKeyRole;
}
