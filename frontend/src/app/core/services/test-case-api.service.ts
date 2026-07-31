import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { BulkOperationResponse, CreateTestCaseRequest, ImportResult, TestCase, TestCaseQuery, TestCaseStatus, UpdateTestCaseRequest } from '../../shared/models/test-case.model';
import { Page } from '../../shared/models/page.model';
import { retryWithBackoff } from '../utils/retry-strategy';

@Injectable({ providedIn: 'root' })
export class TestCaseApiService {
  private readonly http = inject(HttpClient);

  private baseUrl(projectId: string): string {
    return `/api/projects/${projectId}/test-cases`;
  }

  getAll(projectId: string, query: TestCaseQuery = {}): Observable<Page<TestCase>> {
    let params = new HttpParams();
    if (query.q) params = params.set('q', query.q);
    (query.status ?? []).forEach((s) => (params = params.append('status', s)));
    (query.priority ?? []).forEach((p) => (params = params.append('priority', p)));
    (query.label ?? []).forEach((l) => (params = params.append('label', l)));
    if (query.folderId) params = params.set('folderId', query.folderId);
    else if (query.rootOnly) params = params.set('rootOnly', 'true');
    if (query.updatedAfter) params = params.set('updatedAfter', query.updatedAfter);
    if (query.page != null) params = params.set('page', String(query.page));
    if (query.size != null) params = params.set('size', String(query.size));
    if (query.sort) params = params.set('sort', query.sort);
    return this.http.get<Page<TestCase>>(this.baseUrl(projectId), { params }).pipe(retryWithBackoff());
  }

  getById(projectId: string, id: string): Observable<TestCase> {
    return this.http.get<TestCase>(`${this.baseUrl(projectId)}/${id}`).pipe(retryWithBackoff());
  }

  export(projectId: string, format: 'json' | 'csv', excel = false): Observable<Blob> {
    let params = new HttpParams().set('format', format);
    if (excel) {
      params = params.set('excel', 'true');
    }
    return this.http.get(`${this.baseUrl(projectId)}/export`, { params, responseType: 'blob' });
  }

  import(projectId: string, file: File, dryRun: boolean): Observable<ImportResult> {
    const formData = new FormData();
    formData.append('file', file);
    const params = new HttpParams().set('dryRun', String(dryRun));
    return this.http.post<ImportResult>(`${this.baseUrl(projectId)}/import`, formData, { params });
  }

  create(projectId: string, request: CreateTestCaseRequest): Observable<TestCase> {
    return this.http.post<TestCase>(this.baseUrl(projectId), request);
  }

  update(projectId: string, id: string, request: UpdateTestCaseRequest): Observable<TestCase> {
    return this.http.put<TestCase>(`${this.baseUrl(projectId)}/${id}`, request);
  }

  delete(projectId: string, id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl(projectId)}/${id}`);
  }

  bulkUpdateStatus(projectId: string, testCaseIds: string[], status: TestCaseStatus): Observable<BulkOperationResponse> {
    return this.http.post<BulkOperationResponse>(`${this.baseUrl(projectId)}/bulk-status`, { testCaseIds, status });
  }

  bulkDelete(projectId: string, testCaseIds: string[]): Observable<BulkOperationResponse> {
    return this.http.post<BulkOperationResponse>(`${this.baseUrl(projectId)}/bulk-delete`, { testCaseIds });
  }

  uploadStepImage(testStepId: string, file: File): Observable<{ id: string }> {
    const formData = new FormData();
    formData.append('testStepId', testStepId);
    formData.append('file', file);
    return this.http.post<{ id: string }>('/api/step-images', formData);
  }

  getStepImageUrl(imageId: string): string {
    return `/api/step-images/${imageId}`;
  }

  deleteStepImage(imageId: string): Observable<void> {
    return this.http.delete<void>(`/api/step-images/${imageId}`);
  }
}
