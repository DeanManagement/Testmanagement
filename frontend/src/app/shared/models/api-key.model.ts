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
}

export interface ApiKeyCreated {
  id: string;
  name: string;
  keyPrefix: string;
  rawKey: string;
  createdAt: string;
  projectId: string;
  projectName: string;
}

export interface CreateApiKeyRequest {
  name: string;
  projectId: string;
}
