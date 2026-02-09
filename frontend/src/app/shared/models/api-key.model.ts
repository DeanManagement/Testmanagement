export interface ApiKey {
  id: string;
  name: string;
  keyPrefix: string;
  revoked: boolean;
  lastUsedAt: string | null;
  createdAt: string;
}

export interface ApiKeyCreated {
  id: string;
  name: string;
  keyPrefix: string;
  rawKey: string;
  createdAt: string;
}

export interface CreateApiKeyRequest {
  name: string;
}
