import { TestStep } from './test-case.model';

/** A named block of steps kept once per project and used by many test cases (PRD-030). */
export interface SharedStep {
  id: string;
  title: string;
  description: string | null;
  steps: TestStep[];
  /** How many test cases use it: what an edit reaches. */
  usedByCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface SharedStepSummary {
  id: string;
  title: string;
  description: string | null;
  stepCount: number;
  usedByCount: number;
  updatedAt: string;
}

/** A step of a shared step; with id it updates that step in place, so recorded results keep their text. */
export interface SharedStepStepRequest {
  id?: string;
  action: string;
  expectedResult?: string;
  testData?: string;
}

export interface SaveSharedStepRequest {
  title: string;
  description?: string;
  steps: SharedStepStepRequest[];
}

export interface SharedStepUsage {
  testCaseId: string;
  key: string;
  title: string;
}
