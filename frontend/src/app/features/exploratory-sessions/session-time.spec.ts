import { describe, expect, it } from 'vitest';
import { bugPrefillFromNote, elapsedMinutes, noteTypeForShortcut, timeboxProgress } from './session-time';

const START = '2026-09-18T10:00:00Z';

describe('elapsedMinutes', () => {
  it('should count from the start to now while running', () => {
    expect(elapsedMinutes(START, null, new Date('2026-09-18T10:45:30Z'))).toBe(45);
  });

  it('should stop at the end once the session is over', () => {
    expect(elapsedMinutes(START, '2026-09-18T11:30:00Z', new Date('2026-09-19T00:00:00Z'))).toBe(90);
  });

  it('should be zero before the session starts', () => {
    expect(elapsedMinutes(null, null, new Date())).toBe(0);
  });
});

describe('timeboxProgress', () => {
  it('should report the share of the time box used', () => {
    expect(timeboxProgress(30, 60)).toEqual({ percent: 50, overtime: false });
  });

  it('should cap the bar at 100% and flag overtime', () => {
    expect(timeboxProgress(75, 60)).toEqual({ percent: 100, overtime: true });
  });
});

describe('bugPrefillFromNote', () => {
  it('should take the first line as title and the whole note as description', () => {
    expect(bugPrefillFromNote(' JPY total rounds up\nseen on the basket page ')).toEqual({
      title: 'JPY total rounds up',
      description: 'JPY total rounds up\nseen on the basket page',
    });
  });

  it('should keep the title within 255 characters', () => {
    expect(bugPrefillFromNote('x'.repeat(300)).title).toHaveLength(255);
  });
});

describe('noteTypeForShortcut', () => {
  it('should map Alt+1..4 to the note types in order', () => {
    expect(noteTypeForShortcut({ altKey: true, key: '¡', code: 'Digit1' })).toBe('NOTE');
    expect(noteTypeForShortcut({ altKey: true, key: '2', code: 'Digit2' })).toBe('BUG');
    expect(noteTypeForShortcut({ altKey: true, key: '4', code: 'Digit4' })).toBe('IDEA');
  });

  it('should ignore other keys and digits without Alt', () => {
    expect(noteTypeForShortcut({ altKey: true, key: '5', code: 'Digit5' })).toBeNull();
    expect(noteTypeForShortcut({ altKey: false, key: '1', code: 'Digit1' })).toBeNull();
  });
});
