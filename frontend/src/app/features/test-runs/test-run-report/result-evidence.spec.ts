import { StepResult } from '../../../shared/models/test-run.model';
import { resultEvidence } from './result-evidence';

function step(orderIndex: number, status: StepResult['status'], actualResult: string | null): StepResult {
  return { orderIndex, status, actualResult } as StepResult;
}

describe('resultEvidence', () => {
  describe('when the result has a comment', () => {
    it('should use the comment', () => {
      const evidence = resultEvidence({ comment: 'Payment provider down', stepResults: [step(0, 'FAILED', 'HTTP 500')] });

      expect(evidence).toBe('Payment provider down');
    });
  });

  describe('when the result was executed step by step', () => {
    it('should list what was observed at each step that did not pass', () => {
      const evidence = resultEvidence({
        comment: '',
        stepResults: [step(0, 'PASSED', 'Cart opened'), step(1, 'FAILED', 'HTTP 500 on submit'), step(2, 'BLOCKED', 'Could not continue')],
      });

      expect(evidence).toBe('Step 2: HTTP 500 on submit\nStep 3: Could not continue');
    });

    it('should number steps by position even when they arrive out of order', () => {
      const evidence = resultEvidence({ comment: '  ', stepResults: [step(7, 'FAILED', 'second'), step(3, 'PASSED', 'first')] });

      expect(evidence).toBe('Step 2: second');
    });

    it('should ignore a failing step that has no observation', () => {
      expect(resultEvidence({ comment: '', stepResults: [step(0, 'FAILED', ' ')] })).toBe('-');
    });
  });

  describe('when there is nothing to show', () => {
    it('should return a dash', () => {
      expect(resultEvidence({ comment: '', stepResults: [] })).toBe('-');
    });
  });
});
