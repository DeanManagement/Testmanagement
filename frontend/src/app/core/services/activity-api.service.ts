import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ActivityQuery, AuditEntry } from '../../shared/models/activity.model';
import { Page } from '../../shared/models/page.model';

@Injectable({ providedIn: 'root' })
export class ActivityApiService {
  private readonly http = inject(HttpClient);

  private url(projectId: string): string {
    return `/api/projects/${projectId}/activity`;
  }

  /** The project's activity; with entityId one object's history, including comments on it. */
  getActivity(projectId: string, query: ActivityQuery): Observable<Page<AuditEntry>> {
    return this.http.get<Page<AuditEntry>>(this.url(projectId), { params: toParams(query) });
  }

  exportCsv(projectId: string, query: ActivityQuery): Observable<Blob> {
    return this.http.get(`${this.url(projectId)}/export`, { params: toParams(query), responseType: 'blob' });
  }
}

function toParams(query: ActivityQuery): HttpParams {
  let params = new HttpParams();
  if (query.entityId) params = params.set('entityId', query.entityId);
  (query.entityType ?? []).forEach((t) => (params = params.append('entityType', t)));
  (query.userId ?? []).forEach((u) => (params = params.append('userId', u)));
  (query.action ?? []).forEach((a) => (params = params.append('action', a)));
  if (query.from) params = params.set('from', query.from);
  if (query.to) params = params.set('to', query.to);
  if (query.sort) params = params.set('sort', query.sort);
  if (query.page != null) params = params.set('page', String(query.page));
  if (query.size != null) params = params.set('size', String(query.size));
  return params;
}
