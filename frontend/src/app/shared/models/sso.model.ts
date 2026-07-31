export interface SsoProvider {
  id: string;
  slug: string;
  displayName: string;
  issuerUri: string;
  clientId: string;
  /** The client secret is never returned; this only says whether one is stored. */
  secretSet: boolean;
  scopes: string;
  emailClaim: string;
  nameClaim: string;
  adminClaim: string | null;
  adminClaimValue: string | null;
  trustEmailForLinking: boolean;
  autoProvision: boolean;
  active: boolean;
  lastError: string | null;
  lastErrorAt: string | null;
  updatedAt: string;
}

export interface SaveSsoProviderRequest {
  slug: string;
  displayName: string;
  issuerUri: string;
  clientId: string;
  /** Omit to keep the stored secret — the backend treats absent as "unchanged". */
  clientSecret?: string;
  scopes?: string;
  emailClaim?: string;
  nameClaim?: string;
  adminClaim?: string;
  adminClaimValue?: string;
  trustEmailForLinking?: boolean;
  autoProvision?: boolean;
  active?: boolean;
}

export interface AuthSettings {
  localLoginEnabled: boolean;
}

/** Public shape used by the login screen; carries no issuer or client detail. */
export interface AuthConfig {
  localLoginEnabled: boolean;
  providers: { slug: string; displayName: string }[];
}
