import { describe, expect, it } from 'vitest';
import { CustomField, CustomFieldType } from '../../models/custom-field.model';
import { displayValue, isEmptyValue, toControlValue, toRequestValues } from './custom-field-values';

function field(name: string, fieldType: CustomFieldType): CustomField {
  return { id: `id-${name}`, entityType: 'TEST_CASE', name, fieldType, options: [], required: false, archived: false, orderIndex: 0 };
}

describe('custom field values', () => {
  describe('toControlValue', () => {
    it('should start a text field without a value as an empty string', () => {
      expect(toControlValue(field('Customer', 'TEXT'), undefined)).toBe('');
    });

    it('should start a number field without a value as null', () => {
      expect(toControlValue(field('Effort', 'NUMBER'), undefined)).toBeNull();
    });

    it('should start a multi-select without a value as an empty list', () => {
      expect(toControlValue(field('Browsers', 'MULTI_SELECT'), undefined)).toEqual([]);
    });

    it('should keep a stored value', () => {
      expect(toControlValue(field('Effort', 'NUMBER'), 2.5)).toBe(2.5);
      expect(toControlValue(field('Due', 'DATE'), '2026-10-01')).toBe('2026-10-01');
      expect(toControlValue(field('Browsers', 'MULTI_SELECT'), ['Chrome'])).toEqual(['Chrome']);
    });
  });

  describe('isEmptyValue', () => {
    it('should treat null, an empty string and an empty list as empty', () => {
      expect([null, undefined, '', []].every((value) => isEmptyValue(value))).toBe(true);
    });

    it('should treat zero as a value', () => {
      expect(isEmptyValue(0)).toBe(false);
    });
  });

  describe('toRequestValues', () => {
    it('should key values by field name and trim text', () => {
      const fields = [field('Customer', 'TEXT'), field('Effort', 'NUMBER')];

      expect(toRequestValues(fields, { 'id-Customer': ' ACME ', 'id-Effort': 0 })).toEqual({ Customer: 'ACME', Effort: 0 });
    });

    it('should send null for an empty field so the server clears it', () => {
      const fields = [field('Customer', 'TEXT'), field('Browsers', 'MULTI_SELECT')];

      expect(toRequestValues(fields, { 'id-Customer': '', 'id-Browsers': [] })).toEqual({ Customer: null, Browsers: null });
    });

    it('should leave out fields it was not given', () => {
      expect(toRequestValues([], { 'id-Archived': 'kept' })).toEqual({});
    });
  });

  describe('displayValue', () => {
    it('should join multi-select options', () => {
      expect(displayValue(['Chrome', 'Firefox'])).toBe('Chrome, Firefox');
    });

    it('should print numbers and text as they are', () => {
      expect(displayValue(2.5)).toBe('2.5');
      expect(displayValue('Checkout')).toBe('Checkout');
    });
  });
});
