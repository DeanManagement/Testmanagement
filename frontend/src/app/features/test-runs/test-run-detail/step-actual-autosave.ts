/** Saves one step's actual result. */
export type SaveStepActual = (resultId: string, stepId: string, actualResult: string) => void;

interface PendingEdit {
  resultId: string;
  text: string;
  timer: ReturnType<typeof setTimeout>;
}

/**
 * Debounces the actual-result autosave per step (TES-BUG-18). One debounce shared by every step
 * dropped the save of a step as soon as the next one was edited within the delay, so only the last
 * step's text reached the server.
 */
export class StepActualAutosave {
  private readonly pending = new Map<string, PendingEdit>();

  constructor(private readonly save: SaveStepActual, private readonly delayMs = 500) {
  }

  edit(resultId: string, stepId: string, text: string): void {
    this.cancel(stepId);
    this.pending.set(stepId, { resultId, text, timer: setTimeout(() => this.flush(stepId), this.delayMs) });
  }

  /**
   * The step's unsaved text, taken over by the caller: its pending save is cancelled, so a status
   * change can carry the text instead of overwriting it with the stored value.
   */
  take(stepId: string): string | undefined {
    const edit = this.pending.get(stepId);
    this.cancel(stepId);
    return edit?.text;
  }

  /** Saves everything still pending, e.g. when the page is left before the delay ran out. */
  flushAll(): void {
    [...this.pending.keys()].forEach((stepId) => this.flush(stepId));
  }

  private flush(stepId: string): void {
    const edit = this.pending.get(stepId);
    this.cancel(stepId);
    if (edit) {
      this.save(edit.resultId, stepId, edit.text);
    }
  }

  private cancel(stepId: string): void {
    const edit = this.pending.get(stepId);
    if (edit) {
      clearTimeout(edit.timer);
      this.pending.delete(stepId);
    }
  }
}
