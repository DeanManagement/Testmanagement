import { TestStep } from '../models/test-case.model';

/** A step as executed; steps from a shared step carry its title and the reference row's id. */
export interface ExecutedStep extends TestStep {
  /** The case's reference row this step came from, for "convert to local steps". */
  referenceId?: string;
}

/** A case's steps with each shared step replaced by its steps, in place (PRD-030). */
export function expandSteps(steps: TestStep[]): ExecutedStep[] {
  return steps.flatMap((step) => step.sharedStepId
    ? (step.expandedSteps ?? []).map((inner) => ({
        ...inner,
        sharedStepId: step.sharedStepId,
        sharedStepTitle: step.sharedStepTitle,
        referenceId: step.id,
      }))
    : [step]);
}

/**
 * The shared step title to show as a heading above step {@code index}: set on the first step of
 * each run of steps from one shared step, null everywhere else.
 */
export function sharedStepHeadingAt(steps: { sharedStepTitle?: string | null }[], index: number): string | null {
  const title = steps[index]?.sharedStepTitle ?? null;
  if (!title) {
    return null;
  }
  return index > 0 && steps[index - 1].sharedStepTitle === title ? null : title;
}
