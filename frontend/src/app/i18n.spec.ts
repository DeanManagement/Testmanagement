import { describe, expect, it } from 'vitest';
import en from '../assets/i18n/en.json';
import de from '../assets/i18n/de.json';

/**
 * Guards the translation catalogues (EN/DE).
 *
 * <p>These checks exist because the failure mode is silent: ngx-translate renders a missing key as
 * the key itself, so a gap ships looking like `activity.entityTypes.REQUIREMENT` in the UI rather
 * than breaking a build or a test. That is exactly how one slipped in when a new audit entity type
 * was added without its label.
 */
describe('translations', () => {

  type Tree = { [key: string]: string | Tree };

  function flatten(tree: Tree, prefix = ''): Record<string, string> {
    const out: Record<string, string> = {};
    for (const [key, value] of Object.entries(tree)) {
      const path = `${prefix}${key}`;
      if (typeof value === 'string') {
        out[path] = value;
      } else {
        Object.assign(out, flatten(value, `${path}.`));
      }
    }
    return out;
  }

  const flatEn = flatten(en as Tree);
  const flatDe = flatten(de as Tree);

  function params(value: string): string[] {
    return [...value.matchAll(/\{\{\s*(\w+)\s*}}/g)].map((m) => m[1]).sort();
  }

  it('has the same keys in both locales', () => {
    const onlyEn = Object.keys(flatEn).filter((k) => !(k in flatDe));
    const onlyDe = Object.keys(flatDe).filter((k) => !(k in flatEn));

    expect({ onlyEn, onlyDe }).toEqual({ onlyEn: [], onlyDe: [] });
  });

  it('has no empty translations', () => {
    const empty = [
      ...Object.entries(flatEn).filter(([, v]) => !v.trim()).map(([k]) => `en:${k}`),
      ...Object.entries(flatDe).filter(([, v]) => !v.trim()).map(([k]) => `de:${k}`),
    ];

    expect(empty).toEqual([]);
  });

  it('uses the same interpolation parameters in both locales', () => {
    // A German string missing {{count}} would render a sentence with a hole in it.
    const mismatched = Object.keys(flatEn)
      .filter((k) => k in flatDe)
      .filter((k) => params(flatEn[k]).join() !== params(flatDe[k]).join())
      .map((k) => `${k}: en=[${params(flatEn[k])}] de=[${params(flatDe[k])}]`);

    expect(mismatched).toEqual([]);
  });

  /**
   * Enum-derived keys are built by string concatenation in templates
   * (`'activity.entityTypes.' + entry.entityType`), so a new backend enum value has no compile-time
   * link to its label. These lists mirror the Java enums and must be updated alongside them.
   */
  describe('keys built from backend enums', () => {
    const cases: [string, string[]][] = [
      ['priority', ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']],
      ['resultStatus', ['PENDING', 'PASSED', 'FAILED', 'BLOCKED', 'SKIPPED']],
      ['testCaseStatus', ['DRAFT', 'ACTIVE', 'DEPRECATED']],
      ['testRun.status', ['PLANNED', 'IN_PROGRESS', 'COMPLETED', 'ABORTED']],
      ['activity.actions',
        ['CREATED', 'UPDATED', 'DELETED', 'STATUS_CHANGED', 'COMPLETED', 'REOPENED', 'CLONED', 'MOVED']],
      ['activity.entityTypes',
        ['PROJECT', 'TEST_CASE', 'TEST_SUITE', 'TEST_RUN', 'TEST_RESULT', 'COMMENT', 'TEST_PLAN',
          'BUG_REPORT', 'TEST_CASE_FOLDER', 'REQUIREMENT']],
      ['issueTracker.state', ['OPEN', 'CLOSED', 'UNKNOWN']],
      ['issueTracker.providers', ['GITLAB', 'FORGEJO', 'GITHUB', 'JIRA', 'LINEAR']],
      ['requirement.status',
        ['PASSED', 'FAILED', 'BLOCKED', 'SKIPPED', 'UNTESTED', 'UNCOVERED']],
      ['requirement.statusHint',
        ['PASSED', 'FAILED', 'BLOCKED', 'SKIPPED', 'UNTESTED', 'UNCOVERED']],
    ];

    it.each(cases)('%s covers every enum value in both locales', (prefix, values) => {
      const missing = values.flatMap((value) => {
        const key = `${prefix}.${value}`;
        return [
          ...(key in flatEn ? [] : [`en:${key}`]),
          ...(key in flatDe ? [] : [`de:${key}`]),
        ];
      });

      expect(missing).toEqual([]);
    });
  });
});
