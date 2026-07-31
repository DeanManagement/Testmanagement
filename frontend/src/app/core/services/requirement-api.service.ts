import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Page } from '../../shared/models/page.model';
import {
  CoverageSummary,
  Requirement,
  SaveRequirementRequest,
  TraceabilityRow,
} from '../../shared/models/requirement.model';

@Injectable({ providedIn: 'root' })
export class RequirementApiService {
  private readonly http = inject(HttpClient);

  private baseUrl(projectId: string): string {
    return `/api/projects/${projectId}/requirements`;
  }

  getAll(projectId: string, page = 0, size = 50): Observable<Page<Requirement>> {
    const params = new HttpParams().set('page', String(page)).set('size', String(size));
    return this.http.get<Page<Requirement>>(this.baseUrl(projectId), { params });
  }

  create(projectId: string, request: SaveRequirementRequest): Observable<Requirement> {
    return this.http.post<Requirement>(this.baseUrl(projectId), request);
  }

  update(projectId: string, id: string, request: SaveRequirementRequest): Observable<Requirement> {
    return this.http.put<Requirement>(`${this.baseUrl(projectId)}/${id}`, request);
  }

  delete(projectId: string, id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl(projectId)}/${id}`);
  }

  linkTestCase(projectId: string, id: string, testCaseId: string): Observable<Requirement> {
    return this.http.post<Requirement>(`${this.baseUrl(projectId)}/${id}/test-cases/${testCaseId}`, {});
  }

  unlinkTestCase(projectId: string, id: string, testCaseId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl(projectId)}/${id}/test-cases/${testCaseId}`);
  }

  getMatrix(projectId: string): Observable<TraceabilityRow[]> {
    return this.http.get<TraceabilityRow[]>(`/api/projects/${projectId}/traceability`);
  }

  getCoverage(projectId: string): Observable<CoverageSummary> {
    return this.http.get<CoverageSummary>(`/api/projects/${projectId}/traceability/coverage`);
  }
}
