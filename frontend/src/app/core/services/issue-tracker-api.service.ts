import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  CreateIssueLinkRequest,
  IssueLink,
  IssueSearchResult,
  IssueTrackerConfig,
  IssueTrackerProviderType,
  IssueTrackerStatus,
  SaveIssueTrackerConfigRequest,
} from '../../shared/models/issue-tracker.model';

@Injectable({ providedIn: 'root' })
export class IssueTrackerApiService {
  private readonly http = inject(HttpClient);

  private configUrl(projectId: string): string {
    return `/api/projects/${projectId}/issue-tracker`;
  }

  private linksUrl(projectId: string, runId: string, resultId: string): string {
    return `/api/projects/${projectId}/test-runs/${runId}/results/${resultId}/issues`;
  }

  /** Only providers with a working adapter, so the form cannot offer a dead option. */
  getSupportedProviders(projectId: string): Observable<IssueTrackerProviderType[]> {
    return this.http.get<IssueTrackerProviderType[]>(`${this.configUrl(projectId)}/providers`);
  }

  /** Readable by any member: whether linking is possible here, without exposing the config. */
  getStatus(projectId: string): Observable<IssueTrackerStatus> {
    return this.http.get<IssueTrackerStatus>(`${this.configUrl(projectId)}/status`);
  }

  /** Responds 204 with an empty body when the project has no tracker configured. */
  getConfig(projectId: string): Observable<IssueTrackerConfig | null> {
    return this.http.get<IssueTrackerConfig | null>(this.configUrl(projectId));
  }

  saveConfig(projectId: string, request: SaveIssueTrackerConfigRequest): Observable<IssueTrackerConfig> {
    return this.http.put<IssueTrackerConfig>(this.configUrl(projectId), request);
  }

  deleteConfig(projectId: string): Observable<void> {
    return this.http.delete<void>(this.configUrl(projectId));
  }

  testConnection(projectId: string): Observable<void> {
    return this.http.post<void>(`${this.configUrl(projectId)}/test`, {});
  }

  searchIssues(projectId: string, query: string): Observable<IssueSearchResult[]> {
    const params = new HttpParams().set('q', query);
    return this.http.get<IssueSearchResult[]>(`/api/projects/${projectId}/issues/search`, { params });
  }

  getLinks(projectId: string, runId: string, resultId: string): Observable<IssueLink[]> {
    return this.http.get<IssueLink[]>(this.linksUrl(projectId, runId, resultId));
  }

  /** Re-reads state from the tracker; keeps the cached state if the tracker is unreachable. */
  refreshLinks(projectId: string, runId: string, resultId: string): Observable<IssueLink[]> {
    return this.http.post<IssueLink[]>(`${this.linksUrl(projectId, runId, resultId)}/refresh`, {});
  }

  link(
    projectId: string,
    runId: string,
    resultId: string,
    request: CreateIssueLinkRequest,
  ): Observable<IssueLink> {
    return this.http.post<IssueLink>(this.linksUrl(projectId, runId, resultId), request);
  }

  unlink(projectId: string, runId: string, resultId: string, linkId: string): Observable<void> {
    return this.http.delete<void>(`${this.linksUrl(projectId, runId, resultId)}/${linkId}`);
  }
}
