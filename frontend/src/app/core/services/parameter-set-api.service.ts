import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ParameterSet, SaveParameterSetRequest } from '../../shared/models/parameter-set.model';

@Injectable({ providedIn: 'root' })
export class ParameterSetApiService {
  private readonly http = inject(HttpClient);

  private baseUrl(projectId: string, testCaseId: string): string {
    return `/api/projects/${projectId}/test-cases/${testCaseId}/parameter-sets`;
  }

  getAll(projectId: string, testCaseId: string): Observable<ParameterSet[]> {
    return this.http.get<ParameterSet[]>(this.baseUrl(projectId, testCaseId));
  }

  create(projectId: string, testCaseId: string, request: SaveParameterSetRequest): Observable<ParameterSet> {
    return this.http.post<ParameterSet>(this.baseUrl(projectId, testCaseId), request);
  }

  update(
    projectId: string,
    testCaseId: string,
    id: string,
    request: SaveParameterSetRequest,
  ): Observable<ParameterSet> {
    return this.http.put<ParameterSet>(`${this.baseUrl(projectId, testCaseId)}/${id}`, request);
  }

  delete(projectId: string, testCaseId: string, id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl(projectId, testCaseId)}/${id}`);
  }
}
