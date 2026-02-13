import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CreateTestSuiteRequest, TestSuite, TestSuiteReport, UpdateTestSuiteRequest } from '../../shared/models/test-suite.model';

@Injectable({ providedIn: 'root' })
export class TestSuiteApiService {
  private readonly http = inject(HttpClient);

  private baseUrl(projectId: string): string {
    return `/api/projects/${projectId}/test-suites`;
  }

  getAll(projectId: string): Observable<TestSuite[]> {
    return this.http.get<TestSuite[]>(this.baseUrl(projectId));
  }

  getById(projectId: string, id: string): Observable<TestSuite> {
    return this.http.get<TestSuite>(`${this.baseUrl(projectId)}/${id}`);
  }

  getReport(projectId: string, id: string): Observable<TestSuiteReport> {
    return this.http.get<TestSuiteReport>(`${this.baseUrl(projectId)}/${id}/report`);
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
    return this.http.get(`${this.baseUrl(projectId)}/${id}/report/pdf`, { responseType: 'blob' });
  }
}
