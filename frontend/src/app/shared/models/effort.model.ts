/** Effort over a run or plan (PRD-036): all in minutes, from the cases' live estimates. */
export interface EffortSummary {
  estimatedMinutes: number;
  /** Estimates of the results still pending. */
  remainingMinutes: number;
  /** Measured durations, including results later set back to pending. */
  actualMinutes: number;
  /** Pending results whose case has no estimate: "remaining" doesn't count them. */
  pendingUnestimated: number;
}

export interface BurnDownPoint {
  /** A UTC calendar day, yyyy-MM-dd. */
  date: string;
  remainingMinutes: number;
}

export interface BurnDown {
  /** Remaining at the end of each day, up to today. */
  days: BurnDownPoint[];
  /** Straight line to zero on the target date; empty without one. */
  idealLine: BurnDownPoint[];
  scopeStartsAt: string | null;
  /** Set when results executed before execution times were recorded had to be left out. */
  historyAvailableFrom: string | null;
  hasEstimates: boolean;
}

/** Upper bound of a test case estimate: one day. */
export const MAX_ESTIMATE_MINUTES = 1440;
