/**
 * Times each result from the moment it is opened for execution (PRD-036 §3.5): no start button,
 * no pause. The tester corrects the number if they went to lunch. Only a result's first execution
 * is timed: re-opening one that already has a duration must not overwrite it when a status is
 * corrected, which is why a timed value is offered only while nothing was recorded.
 */
export class ExecutionTimer {
  private readonly openedAt = new Map<string, number>();

  constructor(private readonly now: () => number = Date.now) {}

  /** Starts the clock for a result the first time it is opened; re-opening keeps the first start. */
  open(resultId: string): void {
    if (!this.openedAt.has(resultId)) {
      this.openedAt.set(resultId, this.now());
    }
  }

  elapsedMs(resultId: string): number | null {
    const openedAt = this.openedAt.get(resultId);
    return openedAt === undefined ? null : Math.max(0, this.now() - openedAt);
  }

  /**
   * The duration to send with a status change, or undefined to leave the recorded one alone:
   * a manual value always wins; otherwise the timer, but only for a first execution.
   */
  durationFor(resultId: string, recordedDurationMs: number | null, manualMs: number | null): number | undefined {
    if (manualMs !== null) {
      return manualMs;
    }
    if (recordedDurationMs !== null) {
      return undefined;
    }
    return this.elapsedMs(resultId) ?? undefined;
  }
}

/** Durations longer than this ask for confirmation before saving: a timer left running, probably. */
export const LONG_DURATION_MS = 8 * 60 * 60 * 1000;
