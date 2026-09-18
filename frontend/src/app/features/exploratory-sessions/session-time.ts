import { SESSION_NOTE_TYPES, SessionNoteType } from '../../shared/models/exploratory-session.model';

const MS_PER_MINUTE = 60_000;
const MAX_BUG_TITLE = 255;

/** Minutes spent: until the end, or until now while running; 0 before the start. */
export function elapsedMinutes(startedAt: string | null, endedAt: string | null, now: Date): number {
  if (!startedAt) {
    return 0;
  }
  const end = endedAt ? new Date(endedAt) : now;
  return Math.max(0, Math.floor((end.getTime() - new Date(startedAt).getTime()) / MS_PER_MINUTE));
}

/**
 * Progress against the time box for the bar: capped at 100 for the bar's width, with overtime
 * flagged separately. The session keeps running past its time box; nothing stops it.
 */
export function timeboxProgress(elapsed: number, timeboxMinutes: number): { percent: number; overtime: boolean } {
  const percent = timeboxMinutes > 0 ? Math.round((elapsed / timeboxMinutes) * 100) : 0;
  return { percent: Math.min(percent, 100), overtime: elapsed > timeboxMinutes };
}

/** Bug report fields from a BUG note: the first line as title, the whole note as description. */
export function bugPrefillFromNote(body: string): { title: string; description: string } {
  const firstLine = body.trim().split('\n')[0].trim();
  return { title: firstLine.slice(0, MAX_BUG_TITLE), description: body.trim() };
}

/** Alt+1..4 picks a note type (PRD-034 §3.5); anything else is not a shortcut. */
export function noteTypeForShortcut(event: { altKey: boolean; key: string; code?: string }): SessionNoteType | null {
  if (!event.altKey) {
    return null;
  }
  // On macOS Alt changes event.key ("¡" for Alt+1), so the physical key code is the reliable one.
  const digit = event.code?.startsWith('Digit') ? Number(event.code.slice('Digit'.length)) : Number(event.key);
  return digit >= 1 && digit <= SESSION_NOTE_TYPES.length ? SESSION_NOTE_TYPES[digit - 1] : null;
}
