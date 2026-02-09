import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiKey, ApiKeyCreated, CreateApiKeyRequest } from '../../shared/models/api-key.model';

@Injectable({ providedIn: 'root' })
export class ApiKeyApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/api-keys';

  getAll(): Observable<ApiKey[]> {
    return this.http.get<ApiKey[]>(this.baseUrl);
  }

  create(request: CreateApiKeyRequest): Observable<ApiKeyCreated> {
    return this.http.post<ApiKeyCreated>(this.baseUrl, request);
  }

  revoke(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
