import { ProjectResultStatus } from '../models/result-status.model';
import { TestResultStatus } from '../models/test-run.model';

const CATEGORIES: TestResultStatus[] = ['PENDING', 'PASSED', 'FAILED', 'BLOCKED', 'SKIPPED'];

/** The names every project starts with (V81); unchanged, they are shown translated. */
const DEFAULT_NAMES: Record<TestResultStatus, string> = {
  PENDING: 'Pending',
  PASSED: 'Passed',
  FAILED: 'Failed',
  BLOCKED: 'Blocked',
  SKIPPED: 'Skipped',
};

/**
 * TM24: what to pass to the translate pipe for a recorded outcome. A custom name is shown as it is;
 * none, or a default name nobody renamed, reads as the translated category.
 */
export function statusLabelKey(category: TestResultStatus, customName?: string | null): string {
  return !customName || customName === DEFAULT_NAMES[category] ? 'resultStatus.' + category : customName;
}

/**
 * The statuses a picker offers, in the project's order. Without any (they failed to load), one per
 * category with no id, so recording still works and sends the category alone.
 */
export function statusOptions(statuses: readonly ProjectResultStatus[]): ProjectResultStatus[] {
  const active = statuses.filter((s) => !s.archived);
  if (active.length) {
    return active;
  }
  return CATEGORIES.map((category, position) => ({
    id: '', name: DEFAULT_NAMES[category], color: '', category, position, archived: false,
  }));
}

/** A picker value: the status id, or the category for an option without one. */
export function optionKey(option: ProjectResultStatus): string {
  return option.id || option.category;
}

/** Which option a recorded outcome shows as: its custom status, else the first of its category. */
export function selectedKey(options: readonly ProjectResultStatus[], category: TestResultStatus,
                            customStatusId?: string | null): string {
  const match = options.find((o) => o.id && o.id === customStatusId)
    ?? options.find((o) => o.category === category);
  return match ? optionKey(match) : category;
}
