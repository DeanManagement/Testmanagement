export interface Project {
  id: string;
  name: string;
  description: string;
  key: string;
  bugReportsEnabled: boolean;
  /** PRD-033: ACTIVE test cases need an approval by a reviewer. */
  reviewRequired: boolean;
  reviewerMinRole: 'ADMIN' | 'TESTER';
  /** PRD-045: pre-fill a new bug report's empty fields; environment is a default environment name. */
  bugTemplateDescription: string | null;
  bugTemplateSteps: string | null;
  bugTemplateEnvironment: string | null;
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

export interface BugTemplateRequest {
  description: string;
  stepsToReproduce: string;
  environment: string;
}
