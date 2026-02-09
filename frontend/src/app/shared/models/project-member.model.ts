export interface ProjectMember {
  id: string;
  userId: string;
  email: string;
  displayName: string;
  role: ProjectRole;
  createdAt: string;
}

export type ProjectRole = 'ADMIN' | 'TESTER' | 'VIEWER';

export interface AddProjectMemberRequest {
  userId: string;
  role: ProjectRole;
}

export interface UpdateProjectMemberRequest {
  role: ProjectRole;
}
