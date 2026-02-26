import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { BugReport, CreateBugReportRequest, UpdateBugReportRequest } from '../../shared/models/bug-report.model';

@Injectable({ providedIn: 'root' })
export class BugReportApiService {
  private readonly http = inject(HttpClient);

  private baseUrl(projectId: string): string {
    return `/api/projects/${projectId}/bug-reports`;
  }

  getAll(projectId: string): Observable<BugReport[]> {
    return this.http.get<BugReport[]>(this.baseUrl(projectId));
  }

  getById(projectId: string, id: string): Observable<BugReport> {
    return this.http.get<BugReport>(`${this.baseUrl(projectId)}/${id}`);
  }

  getByTestResult(projectId: string, testResultId: string): Observable<BugReport[]> {
    return this.http.get<BugReport[]>(this.baseUrl(projectId), { params: { testResultId } });
  }

  create(projectId: string, request: CreateBugReportRequest): Observable<BugReport> {
    return this.http.post<BugReport>(this.baseUrl(projectId), request);
  }

  update(projectId: string, id: string, request: UpdateBugReportRequest): Observable<BugReport> {
    return this.http.put<BugReport>(`${this.baseUrl(projectId)}/${id}`, request);
  }

  delete(projectId: string, id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl(projectId)}/${id}`);
  }
}
