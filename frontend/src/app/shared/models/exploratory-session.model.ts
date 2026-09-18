import { TestRunStatus } from './test-run.model';

/** PRD-034: what a session note records. BUG notes can be filed as bug reports. */
export type SessionNoteType = 'NOTE' | 'BUG' | 'QUESTION' | 'IDEA';

export const SESSION_NOTE_TYPES: SessionNoteType[] = ['NOTE', 'BUG', 'QUESTION', 'IDEA'];

export interface SessionNote {
  id: string;
  type: SessionNoteType;
  body: string;
  occurredAt: string;
  createdBy: string | null;
  hasImage: boolean;
}

export interface LinkedBug {
  id: string;
  title: string;
  status: string;
}

export interface ExploratorySession {
  id: string;
  key: string;
  projectId: string;
  projectKey: string;
  charter: string;
  timeboxMinutes: number;
  status: TestRunStatus;
  testPlanId: string | null;
  testPlanName: string | null;
  environmentId: string | null;
  environment: string | null;
  testerId: string | null;
  testerName: string | null;
  startedAt: string | null;
  endedAt: string | null;
  summary: string | null;
  createdAt: string;
  updatedAt: string;
  /** Only on the single-session read. */
  notes?: SessionNote[];
  bugs?: LinkedBug[];
}

export interface CreateSessionRequest {
  charter: string;
  timeboxMinutes: number;
  testPlanId?: string;
  environment?: string;
  testerId?: string;
}

export interface UpdateSessionRequest {
  charter?: string;
  timeboxMinutes?: number;
  summary?: string;
}

export const MIN_TIMEBOX_MINUTES = 5;
export const MAX_TIMEBOX_MINUTES = 480;
