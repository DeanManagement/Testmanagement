import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  BuildServerConfig,
  BuildServerProviderType,
  BuildWorkflow,
  DiscoverWorkflowsResponse,
  PipelineRun,
  ProjectWorkflow,
  SaveBuildServerConfigRequest,
  SaveBuildWorkflowRequest,
  TriggerPipelineRequest,
} from '../../shared/models/build-server.model';
import { Page } from '../../shared/models/page.model';

/**
 * PRD-024. The `/api/build-servers` half is instance-admin only; the `/api/projects/...` half is
 * what project members use to trigger assigned workflows and watch runs.
 */
@Injectable({ providedIn: 'root' })
export class BuildServerApiService {
  private readonly http = inject(HttpClient);
  private readonly adminUrl = '/api/build-servers';

  // ---- Global administration -------------------------------------------

  getSupportedProviders(): Observable<BuildServerProviderType[]> {
    return this.http.get<BuildServerProviderType[]>(`${this.adminUrl}/providers`);
  }

  getServers(): Observable<BuildServerConfig[]> {
    return this.http.get<BuildServerConfig[]>(this.adminUrl);
  }

  createServer(request: SaveBuildServerConfigRequest): Observable<BuildServerConfig> {
    return this.http.post<BuildServerConfig>(this.adminUrl, request);
  }

  updateServer(id: string, request: SaveBuildServerConfigRequest): Observable<BuildServerConfig> {
    return this.http.put<BuildServerConfig>(`${this.adminUrl}/${id}`, request);
  }

  deleteServer(id: string): Observable<void> {
    return this.http.delete<void>(`${this.adminUrl}/${id}`);
  }

  testConnection(id: string): Observable<void> {
    return this.http.post<void>(`${this.adminUrl}/${id}/test`, {});
  }

  /** `supported: false` means this provider has no pick-list; fall back to manual entry. */
  discoverWorkflows(id: string, repoRef: string | null): Observable<DiscoverWorkflowsResponse> {
    return this.http.post<DiscoverWorkflowsResponse>(`${this.adminUrl}/${id}/discover`, { repoRef });
  }

  getWorkflows(serverId: string): Observable<BuildWorkflow[]> {
    return this.http.get<BuildWorkflow[]>(`${this.adminUrl}/${serverId}/workflows`);
  }

  createWorkflow(serverId: string, request: SaveBuildWorkflowRequest): Observable<BuildWorkflow> {
    return this.http.post<BuildWorkflow>(`${this.adminUrl}/${serverId}/workflows`, request);
  }

  updateWorkflow(workflowId: string, request: SaveBuildWorkflowRequest): Observable<BuildWorkflow> {
    return this.http.put<BuildWorkflow>(`${this.adminUrl}/workflows/${workflowId}`, request);
  }

  deleteWorkflow(workflowId: string): Observable<void> {
    return this.http.delete<void>(`${this.adminUrl}/workflows/${workflowId}`);
  }

  /** Replaces the workflow's project assignments with exactly this set. */
  assignProjects(workflowId: string, projectIds: string[]): Observable<void> {
    return this.http.put<void>(`${this.adminUrl}/workflows/${workflowId}/projects`, { projectIds });
  }

  // ---- Project side -----------------------------------------------------

  getProjectWorkflows(projectId: string): Observable<ProjectWorkflow[]> {
    return this.http.get<ProjectWorkflow[]>(`/api/projects/${projectId}/workflows`);
  }

  trigger(projectId: string, workflowId: string, request: TriggerPipelineRequest): Observable<PipelineRun> {
    return this.http.post<PipelineRun>(
      `/api/projects/${projectId}/workflows/${workflowId}/trigger`, request);
  }

  getPipelineRuns(projectId: string, page = 0, size = 20): Observable<Page<PipelineRun>> {
    return this.http.get<Page<PipelineRun>>(
      `/api/projects/${projectId}/pipeline-runs?page=${page}&size=${size}`);
  }

  /** Forces an immediate upstream status fetch instead of waiting for the next poll. */
  refreshPipelineRun(projectId: string, runId: string): Observable<PipelineRun> {
    return this.http.post<PipelineRun>(
      `/api/projects/${projectId}/pipeline-runs/${runId}/refresh`, {});
  }
}
