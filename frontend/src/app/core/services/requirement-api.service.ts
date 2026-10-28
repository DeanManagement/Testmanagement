import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Page } from '../../shared/models/page.model';
import { ImportResult } from '../../shared/models/test-case.model';
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

  /** The requirements a test case is linked to (TM08). */
  getForTestCase(projectId: string, testCaseId: string): Observable<Requirement[]> {
    return this.http.get<Requirement[]>(`/api/projects/${projectId}/test-cases/${testCaseId}/requirements`);
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

  /** TM19: CSV upsert by externalId; a dry run writes nothing and reports the same rows. */
  importCsv(projectId: string, file: File, dryRun: boolean): Observable<ImportResult> {
    const formData = new FormData();
    formData.append('file', file);
    const params = new HttpParams().set('dryRun', String(dryRun));
    return this.http.post<ImportResult>(`${this.baseUrl(projectId)}/import`, formData, { params });
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

  /** TM20: the matrix as CSV; with a test plan, results come from its completed runs only. */
  exportMatrixCsv(projectId: string, testPlanId?: string): Observable<Blob> {
    const params = testPlanId ? new HttpParams().set('testPlanId', testPlanId) : new HttpParams();
    return this.http.get(`/api/projects/${projectId}/traceability/export`, { params, responseType: 'blob' });
  }

  getCoverage(projectId: string): Observable<CoverageSummary> {
    return this.http.get<CoverageSummary>(`/api/projects/${projectId}/traceability/coverage`);
  }
}
