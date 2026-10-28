import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
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
import { Attachment } from '../../shared/models/test-case.model';
import { retryWithBackoff } from '../utils/retry-strategy';
import { RunComparison } from '../../shared/models/run-comparison.model';

@Injectable({ providedIn: 'root' })
export class TestRunApiService {
  private readonly http = inject(HttpClient);

  private baseUrl(projectId: string): string {
    return `/api/projects/${projectId}/test-runs`;
  }

  getAll(projectId: string, query: TestRunQuery = {}): Observable<Page<TestRun>> {
    let params = new HttpParams();
    if (query.q) params = params.set('q', query.q);
    (query.status ?? []).forEach((s) => (params = params.append('status', s)));
    if (query.testPlanId) params = params.set('testPlanId', query.testPlanId);
    if (query.executorId) params = params.set('executorId', query.executorId);
    if (query.environmentId) params = params.set('environmentId', query.environmentId);
    if (query.startedAfter) params = params.set('startedAfter', query.startedAfter);
    Object.entries(query.customFieldParams ?? {}).forEach(([name, values]) =>
      values.forEach((value) => (params = params.append(name, value))));
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

  /** How results moved from base to head (PRD-038); without a base the server picks the previous run. */
  compare(projectId: string, head: string, base: string | null, includeUnchanged: boolean): Observable<RunComparison> {
    let params = new HttpParams().set('head', head);
    if (base) params = params.set('base', base);
    if (includeUnchanged) params = params.set('includeUnchanged', 'true');
    return this.http.get<RunComparison>(`${this.baseUrl(projectId)}/compare`, { params });
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

  /** One run per entry of request.environmentIds, named "<name> · <environment>" (PRD-032). */
  createAcrossEnvironments(projectId: string, request: CreateTestRunRequest): Observable<TestRun[]> {
    return this.http.post<TestRun[]>(`${this.baseUrl(projectId)}/across-environments`, request);
  }

  update(projectId: string, id: string, request: UpdateTestRunRequest): Observable<TestRun> {
    return this.http.put<TestRun>(`${this.baseUrl(projectId)}/${id}`, request);
  }

  delete(projectId: string, id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl(projectId)}/${id}`);
  }

  /** TM22: pending results for more cases, at the end of the run; cases already in it are skipped. */
  addTestCases(projectId: string, runId: string, testCaseIds: string[]): Observable<TestRun> {
    return this.http.post<TestRun>(`${this.baseUrl(projectId)}/${runId}/test-cases`, { testCaseIds });
  }

  /** TM22: 409 when a case has a recorded result, unless {@code discardRecordedResults}. */
  removeTestCases(projectId: string, runId: string, testCaseIds: string[], discardRecordedResults: boolean): Observable<TestRun> {
    return this.http.post<TestRun>(`${this.baseUrl(projectId)}/${runId}/test-cases/remove`,
      { testCaseIds, discardRecordedResults });
  }

  /** TM23: null hands the results back to the run's executor. */
  assignResults(projectId: string, runId: string, resultIds: string[], assigneeId: string | null): Observable<TestRun> {
    return this.http.put<TestRun>(`${this.baseUrl(projectId)}/${runId}/results/assignee`, { resultIds, assigneeId });
  }

  /** TM21: every result id of the run, in the new execution order. */
  reorderResults(projectId: string, runId: string, resultIds: string[]): Observable<TestRun> {
    return this.http.put<TestRun>(`${this.baseUrl(projectId)}/${runId}/results/order`, { resultIds });
  }

  clone(projectId: string, runId: string, request: { name: string; environment: string }): Observable<TestRun> {
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

  /** TM24: with customStatusId, its category replaces status on the server. */
  bulkResultStatus(projectId: string, runId: string, resultIds: string[], status: string, cascadeSteps: boolean,
                   customStatusId?: string): Observable<{ affected: number; message: string }> {
    return this.http.post<{ affected: number; message: string }>(
      `${this.baseUrl(projectId)}/${runId}/results/bulk-status`,
      { resultIds, status, cascadeSteps, customStatusId });
  }

  /** PRD-048: with each result's steps, and their screenshots embedded. */
  downloadReportPdf(projectId: string, id: string, options: { steps: boolean; screenshots: boolean }): Observable<Blob> {
    const params = { steps: String(options.steps), screenshots: String(options.screenshots) };
    return this.http.get(`${this.baseUrl(projectId)}/${id}/report/pdf`, { params, responseType: 'blob' }).pipe(retryWithBackoff());
  }

  /** TM05: a step result's evidence files, the collection {@link AttachmentApiService} works on. */
  evidenceUrl(projectId: string, stepResultId: string): string {
    return `/api/projects/${projectId}/step-results/${stepResultId}/attachments`;
  }

  /** TM05: the run's evidence and the files on every step it executes, in one call. */
  getRunStepAttachments(projectId: string, runId: string): Observable<Attachment[]> {
    return this.http.get<Attachment[]>(`${this.baseUrl(projectId)}/${runId}/step-attachments`);
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
