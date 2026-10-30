import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { BugReportStatus, BugStatus, BugStatusTransition } from '../../shared/models/bug-report.model';

export interface SaveBugStatusRequest {
  name?: string;
  color?: string;
  category?: BugReportStatus;
  position?: number;
  archived?: boolean;
}

/** TM47: a project's own bug statuses and the moves allowed between them. */
@Injectable({ providedIn: 'root' })
export class BugStatusApiService {
  private readonly http = inject(HttpClient);

  private url(projectId: string): string {
    return `/api/projects/${projectId}/bug-statuses`;
  }

  getAll(projectId: string, includeArchived = false): Observable<BugStatus[]> {
    return this.http.get<BugStatus[]>(this.url(projectId), { params: { includeArchived } });
  }

  create(projectId: string, request: SaveBugStatusRequest): Observable<BugStatus> {
    return this.http.post<BugStatus>(this.url(projectId), request);
  }

  update(projectId: string, id: string, request: SaveBugStatusRequest): Observable<BugStatus> {
    return this.http.put<BugStatus>(`${this.url(projectId)}/${id}`, request);
  }

  getTransitions(projectId: string): Observable<BugStatusTransition[]> {
    return this.http.get<BugStatusTransition[]>(`${this.url(projectId)}/transitions`);
  }

  /** Replaces the whole table; an empty list allows any move again. */
  saveTransitions(projectId: string, transitions: BugStatusTransition[]): Observable<BugStatusTransition[]> {
    return this.http.put<BugStatusTransition[]>(`${this.url(projectId)}/transitions`, { transitions });
  }
}
