/** An entry of a project's environment catalogue (PRD-032). */
export interface ProjectEnvironment {
  id: string;
  name: string;
  description: string | null;
  sortOrder: number;
  archived: boolean;
  runCount: number;
  bugCount: number;
}

/** Every field optional; omitted fields are left unchanged. */
export interface UpdateEnvironmentRequest {
  name?: string;
  description?: string;
  sortOrder?: number;
  archived?: boolean;
}

/** A test case's latest executed result in one environment; null environment = unspecified. */
export interface EnvironmentResult {
  environmentId: string | null;
  environmentName: string | null;
  status: string;
  runId: string;
  runKey: string;
  executedAt: string;
}
