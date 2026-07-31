import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpEventType, HttpParams, HttpResponse } from '@angular/common/http';
import { BehaviorSubject, Observable } from 'rxjs';
import { filter, map, tap } from 'rxjs/operators';
import {
  CompletionInfo,
  CreateTestRunRequest,
  TestRun,
  TestRunQuery,
  TestRunReport,
  UpdateTestRunRequest,
  CreateTestResultRequest,
  UpdateTestResultRequest,
  UpdateStepResultRequest,
  TestResult,
  StepResult,
} from '../../shared/models/test-run.model';
import { Page } from '../../shared/models/page.model';
import { retryWithBackoff } from '../utils/retry-strategy';

@Injectable({ providedIn: 'root' })
export class TestRunApiService {
  private readonly http = inject(HttpClient);
  readonly uploadProgress$ = new BehaviorSubject<number>(0);

  private baseUrl(projectId: string): string {
    return `/api/projects/${projectId}/test-runs`;
  }

  getAll(projectId: string, query: TestRunQuery = {}): Observable<Page<TestRun>> {
    let params = new HttpParams();
    if (query.q) params = params.set('q', query.q);
    (query.status ?? []).forEach((s) => (params = params.append('status', s)));
    if (query.testPlanId) params = params.set('testPlanId', query.testPlanId);
    if (query.executorId) params = params.set('executorId', query.executorId);
    if (query.startedAfter) params = params.set('startedAfter', query.startedAfter);
    if (query.page != null) params = params.set('page', String(query.page));
    if (query.size != null) params = params.set('size', String(query.size));
    if (query.sort) params = params.set('sort', query.sort);
    return this.http.get<Page<TestRun>>(this.baseUrl(projectId), { params }).pipe(retryWithBackoff());
  }

  getAssignedToMe(statuses?: string[]): Observable<TestRun[]> {
    if (statuses && statuses.length > 0) {
      const params = statuses.map((s) => `statuses=${s}`).join('&');
      return this.http.get<TestRun[]>(`/api/test-runs/assigned-to-me?${params}`).pipe(retryWithBackoff());
    }
    return this.http.get<TestRun[]>('/api/test-runs/assigned-to-me').pipe(retryWithBackoff());
  }

  getById(projectId: string, id: string): Observable<TestRun> {
    return this.http.get<TestRun>(`${this.baseUrl(projectId)}/${id}`).pipe(retryWithBackoff());
  }

  getReport(projectId: string, id: string): Observable<TestRunReport> {
    return this.http.get<TestRunReport>(`${this.baseUrl(projectId)}/${id}/report`).pipe(retryWithBackoff());
  }

  getCompletionInfo(projectId: string, id: string): Observable<CompletionInfo> {
    return this.http.get<CompletionInfo>(`${this.baseUrl(projectId)}/${id}/completion-info`).pipe(retryWithBackoff());
  }

  create(projectId: string, request: CreateTestRunRequest): Observable<TestRun> {
    return this.http.post<TestRun>(this.baseUrl(projectId), request);
  }

  update(projectId: string, id: string, request: UpdateTestRunRequest): Observable<TestRun> {
    return this.http.put<TestRun>(`${this.baseUrl(projectId)}/${id}`, request);
  }

  delete(projectId: string, id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl(projectId)}/${id}`);
  }

  clone(projectId: string, runId: string, request: { name: string; environment?: string }): Observable<TestRun> {
    return this.http.post<TestRun>(`${this.baseUrl(projectId)}/${runId}/clone`, request);
  }

  addResult(projectId: string, runId: string, request: CreateTestResultRequest): Observable<TestResult> {
    return this.http.post<TestResult>(`${this.baseUrl(projectId)}/${runId}/results`, request);
  }

  updateResult(projectId: string, runId: string, resultId: string, request: UpdateTestResultRequest): Observable<TestResult> {
    return this.http.put<TestResult>(`${this.baseUrl(projectId)}/${runId}/results/${resultId}`, request);
  }

  updateStepResult(projectId: string, runId: string, resultId: string, stepResultId: string, request: UpdateStepResultRequest): Observable<StepResult> {
    return this.http.put<StepResult>(`${this.baseUrl(projectId)}/${runId}/results/${resultId}/steps/${stepResultId}`, request);
  }

  bulkResultStatus(projectId: string, runId: string, resultIds: string[], status: string, cascadeSteps: boolean):
    Observable<{ affected: number; message: string }> {
    return this.http.post<{ affected: number; message: string }>(
      `${this.baseUrl(projectId)}/${runId}/results/bulk-status`,
      { resultIds, status, cascadeSteps });
  }

  uploadScreenshot(stepResultId: string, file: File): Observable<{ id: string }> {
    const formData = new FormData();
    formData.append('stepResultId', stepResultId);
    formData.append('file', file);

    this.uploadProgress$.next(0);

    return this.http.post<{ id: string }>('/api/screenshots', formData, {
      reportProgress: true,
      observe: 'events',
    }).pipe(
      tap((event) => {
        if (event.type === HttpEventType.UploadProgress && event.total) {
          this.uploadProgress$.next(Math.round((100 * event.loaded) / event.total));
        }
      }),
      filter((event) => event.type === HttpEventType.Response),
      map((event) => (event as HttpResponse<{ id: string }>).body!)
    );
  }

  deleteScreenshot(screenshotId: string): Observable<void> {
    return this.http.delete<void>(`/api/screenshots/${screenshotId}`);
  }

  downloadReportPdf(projectId: string, id: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl(projectId)}/${id}/report/pdf`, { responseType: 'blob' }).pipe(retryWithBackoff());
  }

  getScreenshotUrl(screenshotId: string): string {
    return `/api/screenshots/${screenshotId}`;
  }

  uploadAllureReport(projectId: string, testRunKey: string, file: File): Observable<{ id: string }> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<{ id: string }>(`${this.baseUrl(projectId)}/${testRunKey}/allure-report`, formData);
  }

  deleteAllureReport(projectId: string, testRunKey: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl(projectId)}/${testRunKey}/allure-report`);
  }

  /**
   * PRD-018: mints a short-lived, single-report view session. The returned token goes into
   * the iframe URL path; the JWT itself never appears in a URL.
   */
  createAllureViewSession(projectId: string, testRunKey: string): Observable<{ token: string }> {
    return this.http.post<{ token: string }>(
      `${this.baseUrl(projectId)}/${testRunKey}/allure-report/session`, {});
  }

  getAllureReportViewUrl(projectId: string, testRunKey: string, viewToken: string): string {
    return `${this.baseUrl(projectId)}/${testRunKey}/allure-report/view/${viewToken}/index.html`;
  }
}
