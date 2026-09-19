import { BurnDown } from '../../../shared/models/effort.model';

/**
 * Every day the chart spans, oldest first: the actual days (plan start to today) and the ideal
 * line's days (scope start to the target date), which may extend past today or start later.
 * Dates are yyyy-MM-dd, so they sort as strings.
 */
export function burnDownDates(burnDown: BurnDown): string[] {
  return [...new Set([...burnDown.days, ...burnDown.idealLine].map((point) => point.date))].sort();
}
