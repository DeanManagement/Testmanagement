import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { StepActualAutosave } from './step-actual-autosave';

/** TES-BUG-18: every step's typed actual result reaches the server, however quickly the next is edited. */
describe('StepActualAutosave', () => {
  let saved: [string, string, string][];
  let autosave: StepActualAutosave;

  beforeEach(() => {
    vi.useFakeTimers();
    saved = [];
    autosave = new StepActualAutosave((resultId, stepId, text) => saved.push([resultId, stepId, text]));
  });

  afterEach(() => vi.useRealTimers());

  it('saves each step, even when the next step is edited within the delay', () => {
    autosave.edit('r1', 's2', 'HTTP 403');
    vi.advanceTimersByTime(100);
    autosave.edit('r1', 's3', 'no config values');
    vi.advanceTimersByTime(100);
    autosave.edit('r1', 's4', 'not the shell');

    vi.advanceTimersByTime(500);

    expect(saved).toEqual([['r1', 's2', 'HTTP 403'], ['r1', 's3', 'no config values'], ['r1', 's4', 'not the shell']]);
  });

  it('saves a step once, with its last text, while it is being typed into', () => {
    autosave.edit('r1', 's1', 'HTTP');
    vi.advanceTimersByTime(300);
    autosave.edit('r1', 's1', 'HTTP 403');

    vi.advanceTimersByTime(500);

    expect(saved).toEqual([['r1', 's1', 'HTTP 403']]);
  });

  it('hands unsaved text to a status change and does not save it again', () => {
    autosave.edit('r1', 's1', 'HTTP 403');

    const taken = autosave.take('s1');
    vi.advanceTimersByTime(500);

    expect(taken).toBe('HTTP 403');
    expect(saved).toEqual([]);
  });

  it('has nothing to hand over for a step without unsaved text', () => {
    expect(autosave.take('s1')).toBeUndefined();
  });

  it('saves pending text right away when the page is left', () => {
    autosave.edit('r1', 's1', 'HTTP 403');

    autosave.flushAll();

    expect(saved).toEqual([['r1', 's1', 'HTTP 403']]);
  });
});
