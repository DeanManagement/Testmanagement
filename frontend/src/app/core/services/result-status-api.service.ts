import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  CreateResultStatusRequest,
  ProjectResultStatus,
  UpdateResultStatusRequest,
} from '../../shared/models/result-status.model';

/** A project's result statuses (TM24). */
@Injectable({ providedIn: 'root' })
export class ResultStatusApiService {
  private readonly http = inject(HttpClient);

  private baseUrl(projectId: string): string {
    return `/api/projects/${projectId}/result-statuses`;
  }

  /** Active statuses in display order, for recording results. */
  getActive(projectId: string): Observable<ProjectResultStatus[]> {
    return this.http.get<ProjectResultStatus[]>(this.baseUrl(projectId));
  }

  /** Every status including archived ones, for the settings page. */
  getAll(projectId: string): Observable<ProjectResultStatus[]> {
    const params = new HttpParams().set('includeArchived', 'true');
    return this.http.get<ProjectResultStatus[]>(this.baseUrl(projectId), { params });
  }

  create(projectId: string, request: CreateResultStatusRequest): Observable<ProjectResultStatus> {
    return this.http.post<ProjectResultStatus>(this.baseUrl(projectId), request);
  }

  update(projectId: string, id: string, request: UpdateResultStatusRequest): Observable<ProjectResultStatus> {
    return this.http.put<ProjectResultStatus>(`${this.baseUrl(projectId)}/${id}`, request);
  }
}
