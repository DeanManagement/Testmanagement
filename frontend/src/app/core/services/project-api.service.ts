import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CreateProjectRequest, Project, UpdateProjectRequest } from '../../shared/models/project.model';
import { ProjectDashboard } from '../../shared/models/dashboard.model';
import { retryWithBackoff } from '../utils/retry-strategy';

@Injectable({ providedIn: 'root' })
export class ProjectApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/projects';

  getAll(): Observable<Project[]> {
    return this.http.get<Project[]>(this.baseUrl).pipe(retryWithBackoff());
  }

  getById(id: string): Observable<Project> {
    return this.http.get<Project>(`${this.baseUrl}/${id}`).pipe(retryWithBackoff());
  }

  create(request: CreateProjectRequest): Observable<Project> {
    return this.http.post<Project>(this.baseUrl, request);
  }

  update(id: string, request: UpdateProjectRequest): Observable<Project> {
    return this.http.put<Project>(`${this.baseUrl}/${id}`, request);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  searchByKey(key: string): Observable<Project[]> {
    return this.http.get<Project[]>(`${this.baseUrl}/search`, { params: { key } }).pipe(retryWithBackoff());
  }

  getDashboard(projectId: string): Observable<ProjectDashboard> {
    return this.http.get<ProjectDashboard>(`${this.baseUrl}/${projectId}/dashboard`).pipe(retryWithBackoff());
  }

  toggleBugReports(projectId: string, enabled: boolean): Observable<Project> {
    return this.http.put<Project>(`${this.baseUrl}/${projectId}/settings/bug-reports`, { enabled });
  }
}
