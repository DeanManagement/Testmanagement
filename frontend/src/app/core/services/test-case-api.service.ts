import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { BulkOperationResponse, CreateTestCaseRequest, TestCase, TestCaseStatus, UpdateTestCaseRequest } from '../../shared/models/test-case.model';

@Injectable({ providedIn: 'root' })
export class TestCaseApiService {
  private readonly http = inject(HttpClient);

  private baseUrl(projectId: string): string {
    return `/api/projects/${projectId}/test-cases`;
  }

  getAll(projectId: string): Observable<TestCase[]> {
    return this.http.get<TestCase[]>(this.baseUrl(projectId));
  }

  getById(projectId: string, id: string): Observable<TestCase> {
    return this.http.get<TestCase>(`${this.baseUrl(projectId)}/${id}`);
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
}
