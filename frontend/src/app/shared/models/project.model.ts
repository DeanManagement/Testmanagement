export interface Project {
  id: string;
  name: string;
  description: string;
  key: string;
  bugReportsEnabled: boolean;
  /** PRD-033: ACTIVE test cases need an approval by a reviewer. */
  reviewRequired: boolean;
  reviewerMinRole: 'ADMIN' | 'TESTER';
  createdAt: string;
  updatedAt: string;
  createdBy?: string;
  updatedBy?: string;
}

export interface CreateProjectRequest {
  name: string;
  description?: string;
}

export interface UpdateProjectRequest {
  name: string;
  description?: string;
}
