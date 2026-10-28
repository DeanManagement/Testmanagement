import { Attachment } from '../models/test-case.model';

/** The same empty list every time, so an `[preloaded]` input does not see a change on each check. */
export const NO_FILES: readonly Attachment[] = Object.freeze([]);

/**
 * TM05: files grouped by the step (`testStepId`) or step result (`stepResultId`) they belong to.
 * Each group is one array for as long as the map lives, which keeps child inputs stable.
 */
export function groupFiles(files: Attachment[], owner: 'testStepId' | 'stepResultId'): Map<string, Attachment[]> {
  const groups = new Map<string, Attachment[]>();
  for (const file of files) {
    const key = file[owner];
    if (key) {
      groups.set(key, [...(groups.get(key) ?? []), file]);
    }
  }
  return groups;
}
