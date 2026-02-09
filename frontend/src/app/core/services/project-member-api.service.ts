import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  ProjectMember,
  AddProjectMemberRequest,
  UpdateProjectMemberRequest,
} from '../../shared/models/project-member.model';

@Injectable({ providedIn: 'root' })
export class ProjectMemberApiService {
  private readonly http = inject(HttpClient);

  getByProject(projectId: string): Observable<ProjectMember[]> {
    return this.http.get<ProjectMember[]>(`/api/projects/${projectId}/members`);
  }

  addMember(projectId: string, request: AddProjectMemberRequest): Observable<ProjectMember> {
    return this.http.post<ProjectMember>(`/api/projects/${projectId}/members`, request);
  }

  updateRole(
    projectId: string,
    memberId: string,
    request: UpdateProjectMemberRequest
  ): Observable<ProjectMember> {
    return this.http.put<ProjectMember>(
      `/api/projects/${projectId}/members/${memberId}`,
      request
    );
  }

  removeMember(projectId: string, memberId: string): Observable<void> {
    return this.http.delete<void>(`/api/projects/${projectId}/members/${memberId}`);
  }
}
