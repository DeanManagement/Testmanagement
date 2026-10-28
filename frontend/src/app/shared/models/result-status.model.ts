import { TestResultStatus } from './test-run.model';

/** A project's own result status (TM24); `category` is the fixed outcome every metric counts. */
export interface ProjectResultStatus {
  id: string;
  name: string;
  /** `#rrggbb`. */
  color: string;
  category: TestResultStatus;
  position: number;
  archived: boolean;
}

export interface CreateResultStatusRequest {
  name: string;
  color: string;
  category: TestResultStatus;
}

/** Every field optional; omitted fields are left unchanged. The category is fixed. */
export interface UpdateResultStatusRequest {
  name?: string;
  color?: string;
  position?: number;
  archived?: boolean;
}
