import { EffortSummary } from '../models/effort.model';

const MINUTES_PER_HOUR = 60;
const MILLIS_PER_MINUTE = 60_000;

/** A duration split for display: "3 h 20 min", or just one part when the other is zero. */
export interface DurationParts {
  hours: number;
  minutes: number;
}

/** Whole minutes, rounded; a sub-minute duration that isn't zero shows as one minute. */
export function millisToMinutes(ms: number): number {
  return ms > 0 ? Math.max(1, Math.round(ms / MILLIS_PER_MINUTE)) : 0;
}

export function minutesToMillis(minutes: number): number {
  return Math.round(minutes * MILLIS_PER_MINUTE);
}

export function durationParts(totalMinutes: number): DurationParts {
  const minutes = Math.max(0, Math.round(totalMinutes));
  return { hours: Math.floor(minutes / MINUTES_PER_HOUR), minutes: minutes % MINUTES_PER_HOUR };
}

/** The translation key and parameters for a duration; the catalogue owns the wording per language. */
export function durationTranslation(totalMinutes: number): { key: string; params: DurationParts } {
  const params = durationParts(totalMinutes);
  if (params.hours === 0) {
    return { key: 'duration.minutes', params };
  }
  return { key: params.minutes === 0 ? 'duration.hours' : 'duration.hoursMinutes', params };
}

/**
 * The same figures as the server's EffortSummary, from the results a run detail holds. Used only so
 * the run header stays current while a tester records results; reports and plans use the server's.
 */
export function effortOf(results: readonly { status: string; estimateMinutes: number | null; durationMs: number | null }[]): EffortSummary {
  let estimatedMinutes = 0;
  let remainingMinutes = 0;
  let actualMs = 0;
  let pendingUnestimated = 0;
  for (const result of results) {
    const pending = result.status === 'PENDING';
    if (result.estimateMinutes != null) {
      estimatedMinutes += result.estimateMinutes;
      remainingMinutes += pending ? result.estimateMinutes : 0;
    } else if (pending) {
      pendingUnestimated++;
    }
    actualMs += result.durationMs ?? 0;
  }
  return { estimatedMinutes, remainingMinutes, actualMinutes: Math.round(actualMs / MILLIS_PER_MINUTE), pendingUnestimated };
}
