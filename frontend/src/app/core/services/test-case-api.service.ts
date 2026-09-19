import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { BulkOperationResponse, TestCaseContext, TestCaseExecution, CreateTestCaseRequest, GherkinPreview, ImportResult, ReviewCapabilities, TestCase, TestCaseQuery, TestCaseStatus, UpdateTestCaseRequest } from '../../shared/models/test-case.model';
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
    if (query.folderId) {
      params = params.set('folderId', query.folderId);
      if (query.includeSubfolders) params = params.set('includeSubfolders', 'true');
    } else if (query.rootOnly) {
      params = params.set('rootOnly', 'true');
    }
    if (query.updatedAfter) params = params.set('updatedAfter', query.updatedAfter);
    Object.entries(query.customFieldParams ?? {}).forEach(([name, values]) =>
      values.forEach((value) => (params = params.append(name, value))));
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

  /**
   * PRD-040: one .feature file, or a ZIP of several. The response is read whole so the caller can
   * take the file name from Content-Disposition. {@code folderId} limits the export to that folder.
   */
  exportFeature(projectId: string, folderId: string | null): Observable<HttpResponse<Blob>> {
    let params = new HttpParams().set('format', 'feature');
    if (folderId) {
      params = params.set('folderId', folderId);
    }
    return this.http.get(`${this.baseUrl(projectId)}/export`, { params, responseType: 'blob', observe: 'response' });
  }

  /** {@code folderId}: where the cases go; Gherkin features become folders under it (PRD-040). */
  import(projectId: string, file: File, dryRun: boolean, folderId: string | null = null): Observable<ImportResult> {
    const formData = new FormData();
    formData.append('file', file);
    let params = new HttpParams().set('dryRun', String(dryRun));
    if (folderId) {
      params = params.set('folderId', folderId);
    }
    return this.http.post<ImportResult>(`${this.baseUrl(projectId)}/import`, formData, { params });
  }

  /** The collection {@link AttachmentApiService} works on (PRD-044). */
  attachmentsUrl(projectId: string, testCaseId: string): string {
    return `${this.baseUrl(projectId)}/${testCaseId}/attachments`;
  }

  /** PRD-050: who made the case, its folder path and the suites that include it. */
  getContext(projectId: string, id: string): Observable<TestCaseContext> {
    return this.http.get<TestCaseContext>(`${this.baseUrl(projectId)}/${id}/context`);
  }

  /** PRD-050: where the case ran or is scheduled to run, newest run first. */
  getExecutions(projectId: string, id: string, page: number, size: number): Observable<Page<TestCaseExecution>> {
    return this.http.get<Page<TestCaseExecution>>(`${this.baseUrl(projectId)}/${id}/executions`,
      { params: { page: String(page), size: String(size) } });
  }

  /** Replaces a shared step reference with copies of its steps, images included (PRD-030). */
  inlineSharedStep(projectId: string, testCaseId: string, stepId: string): Observable<TestCase> {
    return this.http.post<TestCase>(`${this.baseUrl(projectId)}/${testCaseId}/steps/${stepId}/inline`, {});
  }

  /** Reads one scenario; the server writes nothing (PRD-040 §3.6). */
  previewGherkin(projectId: string, text: string): Observable<GherkinPreview> {
    return this.http.post<GherkinPreview>(`${this.baseUrl(projectId)}/gherkin/preview`, text, {
      headers: { 'Content-Type': 'text/plain' },
    });
  }

  create(projectId: string, request: CreateTestCaseRequest): Observable<TestCase> {
    return this.http.post<TestCase>(this.baseUrl(projectId), request);
  }

  update(projectId: string, id: string, request: UpdateTestCaseRequest): Observable<TestCase> {
    return this.http.put<TestCase>(`${this.baseUrl(projectId)}/${id}`, request);
  }

  // ---- Review (PRD-033) ----

  reviewCapabilities(projectId: string, id: string): Observable<ReviewCapabilities> {
    return this.http.get<ReviewCapabilities>(`${this.baseUrl(projectId)}/${id}/review-capabilities`);
  }

  submitForReview(projectId: string, id: string): Observable<TestCase> {
    return this.http.post<TestCase>(`${this.baseUrl(projectId)}/${id}/submit-review`, {});
  }

  /** version: the one the reviewer read; the server refuses (409) if the case moved on since. */
  approve(projectId: string, id: string, version: number): Observable<TestCase> {
    return this.http.post<TestCase>(`${this.baseUrl(projectId)}/${id}/approve`, { version });
  }

  requestChanges(projectId: string, id: string, comment?: string): Observable<TestCase> {
    return this.http.post<TestCase>(`${this.baseUrl(projectId)}/${id}/request-changes`, { comment });
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
