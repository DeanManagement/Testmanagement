import { TestStep } from '../models/test-case.model';
import { expandSteps, sharedStepHeadingAt } from './shared-step-groups';

const step = (id: string, action: string, over: Partial<TestStep> = {}): TestStep =>
  ({ id, action, expectedResult: '', testData: '', orderIndex: 0, imageId: null, ...over });

describe('expandSteps', () => {
  it('replaces a reference by its steps, tagged with the shared step', () => {
    const steps = [
      step('a', 'Reset'),
      step('ref', 'Log in', {
        sharedStepId: 'block', sharedStepTitle: 'Log in',
        expandedSteps: [step('b1', 'Open page'), step('b2', 'Sign in')],
      }),
      step('c', 'Pay'),
    ];

    const expanded = expandSteps(steps);

    expect(expanded.map((s) => s.action)).toEqual(['Reset', 'Open page', 'Sign in', 'Pay']);
    expect(expanded[1]).toMatchObject({ sharedStepTitle: 'Log in', referenceId: 'ref', sharedStepId: 'block' });
    expect(expanded[0].referenceId).toBeUndefined();
  });

  it('turns a reference to an empty shared step into nothing', () => {
    expect(expandSteps([step('ref', 'Empty', { sharedStepId: 'x', expandedSteps: [] })])).toEqual([]);
  });
});

describe('sharedStepHeadingAt', () => {
  const steps = [
    { sharedStepTitle: null }, { sharedStepTitle: 'Log in' }, { sharedStepTitle: 'Log in' },
    { sharedStepTitle: 'Reset' }, { sharedStepTitle: 'Log in' },
  ];

  it('heads the first step of each run of one shared step', () => {
    expect(steps.map((_, i) => sharedStepHeadingAt(steps, i))).toEqual([null, 'Log in', null, 'Reset', 'Log in']);
  });
});
