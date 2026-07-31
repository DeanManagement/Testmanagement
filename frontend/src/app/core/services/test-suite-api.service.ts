import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CreateTestSuiteRequest, TestSuite, TestSuiteQuery, TestSuiteReport, UpdateTestSuiteRequest } from '../../shared/models/test-suite.model';
import { Page } from '../../shared/models/page.model';
import { retryWithBackoff } from '../utils/retry-strategy';

@Injectable({ providedIn: 'root' })
export class TestSuiteApiService {
  private readonly http = inject(HttpClient);

  private baseUrl(projectId: string): string {
    return `/api/projects/${projectId}/test-suites`;
  }

  getAll(projectId: string, query: TestSuiteQuery = {}): Observable<Page<TestSuite>> {
    let params = new HttpParams();
    if (query.q) params = params.set('q', query.q);
    if (query.page != null) params = params.set('page', String(query.page));
    if (query.size != null) params = params.set('size', String(query.size));
    if (query.sort) params = params.set('sort', query.sort);
    return this.http.get<Page<TestSuite>>(this.baseUrl(projectId), { params }).pipe(retryWithBackoff());
  }

  getById(projectId: string, id: string): Observable<TestSuite> {
    return this.http.get<TestSuite>(`${this.baseUrl(projectId)}/${id}`).pipe(retryWithBackoff());
  }

  getReport(projectId: string, id: string): Observable<TestSuiteReport> {
    return this.http.get<TestSuiteReport>(`${this.baseUrl(projectId)}/${id}/report`).pipe(retryWithBackoff());
  }

  create(projectId: string, request: CreateTestSuiteRequest): Observable<TestSuite> {
    return this.http.post<TestSuite>(this.baseUrl(projectId), request);
  }

  update(projectId: string, id: string, request: UpdateTestSuiteRequest): Observable<TestSuite> {
    return this.http.put<TestSuite>(`${this.baseUrl(projectId)}/${id}`, request);
  }

  delete(projectId: string, id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl(projectId)}/${id}`);
  }

  bulkAddTestCases(projectId: string, suiteId: string, testCaseIds: string[]): Observable<void> {
    return this.http.post<void>(`${this.baseUrl(projectId)}/${suiteId}/bulk-add`, { testCaseIds });
  }

  bulkRemoveTestCases(projectId: string, suiteId: string, testCaseIds: string[]): Observable<void> {
    return this.http.post<void>(`${this.baseUrl(projectId)}/${suiteId}/bulk-remove`, { testCaseIds });
  }

  downloadReportPdf(projectId: string, id: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl(projectId)}/${id}/report/pdf`, { responseType: 'blob' }).pipe(retryWithBackoff());
  }
}
