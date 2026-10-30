import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { IssueLink } from '../../shared/models/issue-tracker.model';
import { map } from 'rxjs/operators';
import { BugReport, BugReportQuery, BulkUpdateBugReportsRequest, ChangeBugStatusRequest, CreateBugReportRequest, DefectDashboard, UpdateBugReportRequest, BugRelationType, BugReportRelations } from '../../shared/models/bug-report.model';
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
    let params = filterParams(query);
    if (query.page != null) params = params.set('page', String(query.page));
    if (query.size != null) params = params.set('size', String(query.size));
    return this.http.get<Page<BugReport>>(this.baseUrl(projectId), { params }).pipe(retryWithBackoff());
  }

  /** TM48: every bug the filter matches, as CSV, in the list's sort order. */
  exportCsv(projectId: string, query: BugReportQuery): Observable<Blob> {
    return this.http.get(`${this.baseUrl(projectId)}/export.csv`, { params: filterParams(query), responseType: 'blob' });
  }

  /** TM49: labels already used on the project's bugs. */
  getLabels(projectId: string): Observable<string[]> {
    return this.http.get<string[]>(`${this.baseUrl(projectId)}/labels`);
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

  /** TM50: the work item the bug was handed over to, or null. */
  getTrackerIssue(projectId: string, idOrKey: string): Observable<IssueLink | null> {
    return this.http.get<IssueLink | null>(`${this.baseUrl(projectId)}/${idOrKey}/tracker-issue`);
  }

  /** TM50: files a work item, or links {@code existingExternalId}; the bug's status follows it. */
  handOver(projectId: string, idOrKey: string, existingExternalId?: string): Observable<IssueLink> {
    return this.http.post<IssueLink>(`${this.baseUrl(projectId)}/${idOrKey}/tracker-issue`, { existingExternalId });
  }

  refreshTrackerIssue(projectId: string, idOrKey: string): Observable<IssueLink> {
    return this.http.post<IssueLink>(`${this.baseUrl(projectId)}/${idOrKey}/tracker-issue/refresh`, {});
  }

  detachTrackerIssue(projectId: string, idOrKey: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl(projectId)}/${idOrKey}/tracker-issue`);
  }

  /** TM46: test cases, requirements and bugs linked by hand. */
  getRelations(projectId: string, idOrKey: string): Observable<BugReportRelations> {
    return this.http.get<BugReportRelations>(`${this.baseUrl(projectId)}/${idOrKey}/relations`);
  }

  /** {@code kind}: 'test-cases' or 'requirements'; {@code reference} a key, external id or UUID. */
  linkItem(projectId: string, idOrKey: string, kind: 'test-cases' | 'requirements', reference: string): Observable<BugReportRelations> {
    return this.http.post<BugReportRelations>(`${this.baseUrl(projectId)}/${idOrKey}/relations/${kind}`, { reference });
  }

  unlinkItem(projectId: string, idOrKey: string, kind: 'test-cases' | 'requirements', reference: string): Observable<BugReportRelations> {
    return this.http.delete<BugReportRelations>(`${this.baseUrl(projectId)}/${idOrKey}/relations/${kind}/${encodeURIComponent(reference)}`);
  }

  relate(projectId: string, idOrKey: string, relatedBug: string, type: BugRelationType): Observable<BugReportRelations> {
    return this.http.post<BugReportRelations>(`${this.baseUrl(projectId)}/${idOrKey}/relations/bugs`, { relatedBug, type });
  }

  unrelate(projectId: string, idOrKey: string, relationId: string): Observable<BugReportRelations> {
    return this.http.delete<BugReportRelations>(`${this.baseUrl(projectId)}/${idOrKey}/relations/bugs/${relationId}`);
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

function filterParams(query: BugReportQuery): HttpParams {
  let params = new HttpParams();
  if (query.q) params = params.set('q', query.q);
  (query.status ?? []).forEach((s) => (params = params.append('status', s)));
  (query.priority ?? []).forEach((p) => (params = params.append('priority', p)));
  (query.assignee ?? []).forEach((a) => (params = params.append('assignee', a)));
  (query.reporter ?? []).forEach((r) => (params = params.append('reporter', r)));
  (query.label ?? []).forEach((l) => (params = params.append('label', l)));
  (query.statusId ?? []).forEach((id) => (params = params.append('statusId', id)));
  if (query.testResultId) params = params.set('testResultId', query.testResultId);
  if (query.environmentId) params = params.set('environmentId', query.environmentId);
  if (query.testRunId) params = params.set('testRunId', query.testRunId);
  if (query.createdFrom) params = params.set('createdFrom', query.createdFrom);
  if (query.createdTo) params = params.set('createdTo', query.createdTo);
  if (query.sort) params = params.set('sort', query.sort);
  return params;
}
