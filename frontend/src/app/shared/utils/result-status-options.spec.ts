import { describe, expect, it } from 'vitest';
import { ProjectResultStatus } from '../models/result-status.model';
import { optionKey, selectedKey, statusLabelKey, statusOptions } from './result-status-options';

function status(id: string, name: string, category: ProjectResultStatus['category'], archived = false): ProjectResultStatus {
  return { id, name, color: '#000000', category, position: 0, archived };
}

describe('result-status-options', () => {
  describe('statusLabelKey', () => {
    it('translates an outcome without a custom status', () => {
      expect(statusLabelKey('SKIPPED', null)).toBe('resultStatus.SKIPPED');
    });

    it('translates a default name nobody renamed', () => {
      expect(statusLabelKey('PASSED', 'Passed')).toBe('resultStatus.PASSED');
    });

    it('shows a custom name as it is', () => {
      expect(statusLabelKey('SKIPPED', 'Not applicable')).toBe('Not applicable');
    });
  });

  describe('statusOptions', () => {
    it('leaves archived statuses out', () => {
      const options = statusOptions([status('a', 'Passed', 'PASSED'), status('b', 'Old', 'PASSED', true)]);

      expect(options.map((o) => o.id)).toEqual(['a']);
    });

    it('falls back to one option per category, keyed by the category, when there are none', () => {
      const options = statusOptions([]);

      expect(options.map(optionKey)).toEqual(['PENDING', 'PASSED', 'FAILED', 'BLOCKED', 'SKIPPED']);
    });
  });

  describe('selectedKey', () => {
    const options = [status('s', 'Skipped', 'SKIPPED'), status('na', 'Not applicable', 'SKIPPED')];

    it('selects the recorded custom status', () => {
      expect(selectedKey(options, 'SKIPPED', 'na')).toBe('na');
    });

    it('selects the first status of the category when none was recorded', () => {
      expect(selectedKey(options, 'SKIPPED', null)).toBe('s');
    });
  });
});
