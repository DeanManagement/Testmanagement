import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  CompletionInfo,
  CreateTestRunRequest,
  TestRun,
  TestRunReport,
  UpdateTestRunRequest,
  CreateTestResultRequest,
  UpdateTestResultRequest,
  UpdateStepResultRequest,
  TestResult,
  StepResult,
} from '../../shared/models/test-run.model';

@Injectable({ providedIn: 'root' })
export class TestRunApiService {
  private readonly http = inject(HttpClient);

  private baseUrl(projectId: string): string {
    return `/api/projects/${projectId}/test-runs`;
  }

  getAll(projectId: string): Observable<TestRun[]> {
    return this.http.get<TestRun[]>(this.baseUrl(projectId));
  }

  getById(projectId: string, id: string): Observable<TestRun> {
    return this.http.get<TestRun>(`${this.baseUrl(projectId)}/${id}`);
  }

  getReport(projectId: string, id: string): Observable<TestRunReport> {
    return this.http.get<TestRunReport>(`${this.baseUrl(projectId)}/${id}/report`);
  }

  getCompletionInfo(projectId: string, id: string): Observable<CompletionInfo> {
    return this.http.get<CompletionInfo>(`${this.baseUrl(projectId)}/${id}/completion-info`);
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

  uploadScreenshot(stepResultId: string, file: File): Observable<{ id: string }> {
    const formData = new FormData();
    formData.append('stepResultId', stepResultId);
    formData.append('file', file);
    return this.http.post<{ id: string }>('/api/screenshots', formData);
  }

  deleteScreenshot(screenshotId: string): Observable<void> {
    return this.http.delete<void>(`/api/screenshots/${screenshotId}`);
  }

  downloadReportPdf(projectId: string, id: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl(projectId)}/${id}/report/pdf`, { responseType: 'blob' });
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

  getAllureReportViewUrl(projectId: string, testRunKey: string): string {
    return `${this.baseUrl(projectId)}/${testRunKey}/allure-report/view/index.html`;
  }
}
