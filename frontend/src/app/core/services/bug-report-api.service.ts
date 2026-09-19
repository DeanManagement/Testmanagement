import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import {
  BugReport,
  BugReportQuery,
  BulkUpdateBugReportsRequest,
  ChangeBugStatusRequest,
  CreateBugReportRequest,
  DefectDashboard,
  UpdateBugReportRequest,
} from '../../shared/models/bug-report.model';
import { Page } from '../../shared/models/page.model';
import { BulkOperationResponse } from '../../shared/models/test-case.model';
import { retryWithBackoff } from '../utils/retry-strategy';

@Injectable({ providedIn: 'root' })
export class BugReportApiService {
  private readonly http = inject(HttpClient);

  private baseUrl(projectId: string): string {
    return `/api/projects/${projectId}/bug-reports`;
  }

  getAll(projectId: string, query: BugReportQuery = {}): Observable<Page<BugReport>> {
    let params = new HttpParams();
    if (query.q) params = params.set('q', query.q);
    (query.status ?? []).forEach((s) => (params = params.append('status', s)));
    (query.priority ?? []).forEach((p) => (params = params.append('priority', p)));
    (query.assignee ?? []).forEach((a) => (params = params.append('assignee', a)));
    if (query.testResultId) params = params.set('testResultId', query.testResultId);
    if (query.page != null) params = params.set('page', String(query.page));
    if (query.size != null) params = params.set('size', String(query.size));
    if (query.sort) params = params.set('sort', query.sort);
    return this.http.get<Page<BugReport>>(this.baseUrl(projectId), { params }).pipe(retryWithBackoff());
  }

  /** By UUID or by key (PROJ-BUG-12). */
  getById(projectId: string, idOrKey: string): Observable<BugReport> {
    return this.http.get<BugReport>(`${this.baseUrl(projectId)}/${idOrKey}`).pipe(retryWithBackoff());
  }

  getByTestResult(projectId: string, testResultId: string): Observable<BugReport[]> {
    return this.getAll(projectId, { testResultId, size: 200 }).pipe(map((page) => page.content));
  }

  /** The collection {@link AttachmentApiService} works on (PRD-051). */
  attachmentsUrl(projectId: string, bugId: string): string {
    return `${this.baseUrl(projectId)}/${bugId}/attachments`;
  }

  create(projectId: string, request: CreateBugReportRequest): Observable<BugReport> {
    return this.http.post<BugReport>(this.baseUrl(projectId), request);
  }

  update(projectId: string, id: string, request: UpdateBugReportRequest): Observable<BugReport> {
    return this.http.put<BugReport>(`${this.baseUrl(projectId)}/${id}`, request);
  }

  changeStatus(projectId: string, id: string, request: ChangeBugStatusRequest): Observable<BugReport> {
    return this.http.patch<BugReport>(`${this.baseUrl(projectId)}/${id}/status`, request);
  }

  bulkUpdate(projectId: string, request: BulkUpdateBugReportsRequest): Observable<BulkOperationResponse> {
    return this.http.patch<BulkOperationResponse>(`${this.baseUrl(projectId)}/bulk`, request);
  }

  bulkDelete(projectId: string, ids: string[]): Observable<BulkOperationResponse> {
    return this.http.post<BulkOperationResponse>(`${this.baseUrl(projectId)}/bulk-delete`, { ids });
  }

  /** PRD-047: the bug showed up again in this result (and step). Idempotent. */
  link(projectId: string, idOrKey: string, testResultId: string, stepResultId?: string | null): Observable<BugReport> {
    return this.http.post<BugReport>(`${this.baseUrl(projectId)}/${idOrKey}/links`, { testResultId, stepResultId });
  }

  unlink(projectId: string, idOrKey: string, linkId: string): Observable<BugReport> {
    return this.http.delete<BugReport>(`${this.baseUrl(projectId)}/${idOrKey}/links/${linkId}`);
  }

  /** Bugs found in, or linked to, the plan's runs. */
  getByTestPlan(projectId: string, planId: string): Observable<BugReport[]> {
    return this.http.get<BugReport[]>(`/api/projects/${projectId}/test-plans/${planId}/bug-reports`);
  }

  getDefectDashboard(projectId: string): Observable<DefectDashboard> {
    return this.http.get<DefectDashboard>(`/api/projects/${projectId}/dashboard/defects`);
  }

  delete(projectId: string, id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl(projectId)}/${id}`);
  }

  getAssignedToMe(): Observable<BugReport[]> {
    return this.http.get<BugReport[]>('/api/bug-reports/assigned-to-me').pipe(retryWithBackoff());
  }
}
