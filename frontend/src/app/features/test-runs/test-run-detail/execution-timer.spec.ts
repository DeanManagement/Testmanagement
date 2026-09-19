import { describe, expect, it } from 'vitest';
import { ExecutionTimer } from './execution-timer';

function timerAt(start: number): { timer: ExecutionTimer; advance: (ms: number) => void } {
  let now = start;
  return { timer: new ExecutionTimer(() => now), advance: (ms) => (now += ms) };
}

describe('ExecutionTimer', () => {
  it('should know nothing about a result that was never opened', () => {
    expect(new ExecutionTimer().elapsedMs('r1')).toBeNull();
  });

  it('should time a result from when it was opened', () => {
    const { timer, advance } = timerAt(1_000);
    timer.open('r1');
    advance(90_000);

    expect(timer.elapsedMs('r1')).toBe(90_000);
  });

  it('should keep the first start when a result is opened again', () => {
    const { timer, advance } = timerAt(0);
    timer.open('r1');
    advance(60_000);
    timer.open('r1');
    advance(30_000);

    expect(timer.elapsedMs('r1')).toBe(90_000);
  });

  describe('durationFor', () => {
    it('should send the timed value on a first execution', () => {
      const { timer, advance } = timerAt(0);
      timer.open('r1');
      advance(45_000);

      expect(timer.durationFor('r1', null, null)).toBe(45_000);
    });

    it('should leave a recorded duration alone when a status is corrected', () => {
      const { timer, advance } = timerAt(0);
      timer.open('r1');
      advance(45_000);

      expect(timer.durationFor('r1', 600_000, null)).toBeUndefined();
    });

    it('should prefer a value the tester typed', () => {
      const { timer } = timerAt(0);
      timer.open('r1');

      expect(timer.durationFor('r1', 600_000, 120_000)).toBe(120_000);
    });

    it('should send nothing for a result that was never opened, such as a bulk change', () => {
      expect(new ExecutionTimer().durationFor('r1', null, null)).toBeUndefined();
    });
  });
});
