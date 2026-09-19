import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Page } from '../../shared/models/page.model';
import {
  SaveSharedStepRequest,
  SharedStep,
  SharedStepSummary,
  SharedStepUsage,
} from '../../shared/models/shared-step.model';

/** Shared steps (PRD-030): reusable blocks of steps, kept once per project. */
@Injectable({ providedIn: 'root' })
export class SharedStepApiService {
  private readonly http = inject(HttpClient);

  private baseUrl(projectId: string): string {
    return `/api/projects/${projectId}/shared-steps`;
  }

  /** {@code query} matches part of the title, ignoring case. */
  list(projectId: string, query = '', size = 50): Observable<Page<SharedStepSummary>> {
    let params = new HttpParams().set('size', String(size));
    if (query.trim()) {
      params = params.set('q', query.trim());
    }
    return this.http.get<Page<SharedStepSummary>>(this.baseUrl(projectId), { params });
  }

  get(projectId: string, id: string): Observable<SharedStep> {
    return this.http.get<SharedStep>(`${this.baseUrl(projectId)}/${id}`);
  }

  usages(projectId: string, id: string): Observable<SharedStepUsage[]> {
    return this.http.get<SharedStepUsage[]>(`${this.baseUrl(projectId)}/${id}/usages`);
  }

  create(projectId: string, request: SaveSharedStepRequest): Observable<SharedStep> {
    return this.http.post<SharedStep>(this.baseUrl(projectId), request);
  }

  update(projectId: string, id: string, request: SaveSharedStepRequest): Observable<SharedStep> {
    return this.http.put<SharedStep>(`${this.baseUrl(projectId)}/${id}`, request);
  }

  /** Refused (409) while test cases use it. */
  delete(projectId: string, id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl(projectId)}/${id}`);
  }
}
