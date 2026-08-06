import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  AuthConfig,
  AuthSettings,
  SaveSsoProviderRequest,
  SsoProvider,
} from '../../shared/models/sso.model';

@Injectable({ providedIn: 'root' })
export class SsoApiService {
  private readonly http = inject(HttpClient);
  private readonly adminUrl = '/api/admin/sso';

  /** Public: the login screen calls this before anyone is authenticated. */
  getAuthConfig(): Observable<AuthConfig> {
    return this.http.get<AuthConfig>('/api/auth/config');
  }

  getProviders(): Observable<SsoProvider[]> {
    return this.http.get<SsoProvider[]>(`${this.adminUrl}/providers`);
  }

  createProvider(request: SaveSsoProviderRequest): Observable<SsoProvider> {
    return this.http.post<SsoProvider>(`${this.adminUrl}/providers`, request);
  }

  updateProvider(id: string, request: SaveSsoProviderRequest): Observable<SsoProvider> {
    return this.http.put<SsoProvider>(`${this.adminUrl}/providers/${id}`, request);
  }

  deleteProvider(id: string): Observable<void> {
    return this.http.delete<void>(`${this.adminUrl}/providers/${id}`);
  }

  /**
   * Reads the issuer's discovery document, or for a GitHub provider asks its API whether it is
   * reachable; 502 if it is not, or is not what it claims to be.
   */
  testProvider(id: string): Observable<void> {
    return this.http.post<void>(`${this.adminUrl}/providers/${id}/test`, {});
  }

  getSettings(): Observable<AuthSettings> {
    return this.http.get<AuthSettings>(`${this.adminUrl}/settings`);
  }

  updateSettings(localLoginEnabled: boolean): Observable<AuthSettings> {
    return this.http.put<AuthSettings>(`${this.adminUrl}/settings`, { localLoginEnabled });
  }
}
