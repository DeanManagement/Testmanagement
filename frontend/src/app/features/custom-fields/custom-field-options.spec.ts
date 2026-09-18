import { describe, expect, it } from 'vitest';
import { CustomField } from '../../shared/models/custom-field.model';
import { optionChanges, optionsError, orderIndexChanges, toOptionRows } from './custom-field-options';

function field(id: string, orderIndex: number): CustomField {
  return { id, entityType: 'TEST_CASE', name: id, fieldType: 'TEXT', options: [], required: false, archived: false, orderIndex };
}

describe('custom field options', () => {
  describe('optionChanges', () => {
    it('should report an edited row as a rename', () => {
      const rows = toOptionRows(['Checkout', 'Search']);
      rows[1].label = 'Find';

      expect(optionChanges(rows)).toEqual({ options: ['Checkout', 'Find'], renamedOptions: { Search: 'Find' } });
    });

    it('should report a deleted row as a removal, not a rename', () => {
      const rows = toOptionRows(['Checkout', 'Search']).slice(0, 1);

      expect(optionChanges(rows)).toEqual({ options: ['Checkout'], renamedOptions: {} });
    });

    it('should add new rows, trimmed, and drop blank ones', () => {
      const rows = [...toOptionRows(['Checkout']), { original: null, label: ' Admin ' }, { original: null, label: '  ' }];

      expect(optionChanges(rows)).toEqual({ options: ['Checkout', 'Admin'], renamedOptions: {} });
    });
  });

  describe('optionsError', () => {
    it('should accept distinct labels', () => {
      expect(optionsError(toOptionRows(['Checkout', 'Search']), 50)).toBeNull();
    });

    it('should require at least one non-blank option', () => {
      expect(optionsError([{ original: null, label: ' ' }], 50)).toBe('customField.dialog.optionsRequired');
    });

    it('should refuse duplicates ignoring case', () => {
      expect(optionsError(toOptionRows(['Checkout', 'checkout']), 50)).toBe('customField.dialog.duplicateOption');
    });

    it('should refuse more options than allowed', () => {
      expect(optionsError(toOptionRows(['A', 'B', 'C']), 2)).toBe('customField.dialog.tooManyOptions');
    });

    it('should refuse the CSV separator', () => {
      expect(optionsError(toOptionRows(['A;B']), 50)).toBe('customField.dialog.optionSeparator');
    });
  });

  describe('orderIndexChanges', () => {
    it('should return only the fields whose position changed', () => {
      const ordered = [field('a', 0), field('c', 2), field('b', 1)];

      expect(orderIndexChanges(ordered)).toEqual([{ id: 'c', orderIndex: 1 }, { id: 'b', orderIndex: 2 }]);
    });

    it('should return nothing when the order is unchanged', () => {
      expect(orderIndexChanges([field('a', 0), field('b', 1)])).toEqual([]);
    });
  });
});
