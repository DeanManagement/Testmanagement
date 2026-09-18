import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, shareReplay, tap } from 'rxjs';
import { EnvironmentResult, ProjectEnvironment, UpdateEnvironmentRequest } from '../../shared/models/environment.model';

/**
 * The environment catalogue (PRD-032). The active list is cached per project because every run
 * and bug form asks for it; any change made through this service drops that project's cache.
 */
@Injectable({ providedIn: 'root' })
export class EnvironmentApiService {
  private readonly http = inject(HttpClient);
  private readonly activeCache = new Map<string, Observable<ProjectEnvironment[]>>();

  private baseUrl(projectId: string): string {
    return `/api/projects/${projectId}/environments`;
  }

  /** Active environments, for pickers and filters. Cached. */
  getActive(projectId: string): Observable<ProjectEnvironment[]> {
    let cached = this.activeCache.get(projectId);
    if (!cached) {
      cached = this.http.get<ProjectEnvironment[]>(this.baseUrl(projectId)).pipe(shareReplay(1));
      this.activeCache.set(projectId, cached);
    }
    return cached;
  }

  /** Every environment including archived ones, for the settings page. Not cached. */
  getAll(projectId: string): Observable<ProjectEnvironment[]> {
    const params = new HttpParams().set('includeArchived', 'true');
    return this.http.get<ProjectEnvironment[]>(this.baseUrl(projectId), { params });
  }

  create(projectId: string, name: string, description?: string): Observable<ProjectEnvironment> {
    return this.http.post<ProjectEnvironment>(this.baseUrl(projectId), { name, description })
      .pipe(tap(() => this.invalidate(projectId)));
  }

  update(projectId: string, id: string, request: UpdateEnvironmentRequest): Observable<ProjectEnvironment> {
    return this.http.put<ProjectEnvironment>(`${this.baseUrl(projectId)}/${id}`, request)
      .pipe(tap(() => this.invalidate(projectId)));
  }

  delete(projectId: string, id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl(projectId)}/${id}`)
      .pipe(tap(() => this.invalidate(projectId)));
  }

  merge(projectId: string, sourceId: string, targetId: string): Observable<ProjectEnvironment> {
    return this.http.post<ProjectEnvironment>(`${this.baseUrl(projectId)}/${sourceId}/merge`, { targetId })
      .pipe(tap(() => this.invalidate(projectId)));
  }

  latestResultsByEnvironment(projectId: string, testCaseId: string): Observable<EnvironmentResult[]> {
    return this.http.get<EnvironmentResult[]>(
      `/api/projects/${projectId}/test-cases/${testCaseId}/results/by-environment`);
  }

  /** Call after a write elsewhere may have registered a new name (a run or bug saved by name). */
  invalidate(projectId: string): void {
    this.activeCache.delete(projectId);
  }
}
