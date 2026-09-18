import { convertToParamMap } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { CustomField, CustomFieldType } from '../../models/custom-field.model';
import { isActive, readCustomFieldParams, readFilterState, toQueryParams } from './custom-field-filter-params';

function field(name: string, fieldType: CustomFieldType): CustomField {
  return { id: name, entityType: 'TEST_CASE', name, fieldType, options: [], required: false, archived: false, orderIndex: 0 };
}

const EMPTY = { options: [], text: '', min: '', max: '' };

describe('custom field filter params', () => {
  describe('readCustomFieldParams', () => {
    it('should keep only cf. parameters, with every repeated value', () => {
      const params = convertToParamMap({ status: 'ACTIVE', 'cf.Component': ['Checkout', 'Search'], 'cf.Effort.min': '2' });

      expect(readCustomFieldParams(params)).toEqual({ 'cf.Component': ['Checkout', 'Search'], 'cf.Effort.min': ['2'] });
    });
  });

  describe('readFilterState', () => {
    it('should read the chosen options of a select', () => {
      const params = convertToParamMap({ 'cf.Component': ['Checkout', 'Search'] });

      expect(readFilterState(field('Component', 'SELECT'), params)).toEqual({ ...EMPTY, options: ['Checkout', 'Search'] });
    });

    it('should read the bounds of a number field', () => {
      const params = convertToParamMap({ 'cf.Effort.min': '2', 'cf.Effort.max': '8' });

      expect(readFilterState(field('Effort', 'NUMBER'), params)).toEqual({ ...EMPTY, min: '2', max: '8' });
    });

    it('should read the substring of a text field', () => {
      expect(readFilterState(field('Customer', 'TEXT'), convertToParamMap({ 'cf.Customer': 'acme' })))
        .toEqual({ ...EMPTY, text: 'acme' });
    });
  });

  describe('toQueryParams', () => {
    it('should repeat the parameter for each chosen option', () => {
      expect(toQueryParams(field('Browsers', 'MULTI_SELECT'), { ...EMPTY, options: ['Chrome', 'Firefox'] }))
        .toEqual({ 'cf.Browsers': ['Chrome', 'Firefox'] });
    });

    it('should write bounds as .min and .max, removing an empty one', () => {
      expect(toQueryParams(field('Due', 'DATE'), { ...EMPTY, min: '2026-10-01' }))
        .toEqual({ 'cf.Due.min': '2026-10-01', 'cf.Due.max': null });
    });

    it('should remove the parameter of a cleared field', () => {
      expect(toQueryParams(field('Customer', 'TEXT'), { ...EMPTY, text: '  ' })).toEqual({ 'cf.Customer': null });
    });
  });

  describe('isActive', () => {
    it('should be false for an untouched field and true once anything is set', () => {
      expect(isActive(EMPTY)).toBe(false);
      expect(isActive({ ...EMPTY, max: '5' })).toBe(true);
    });
  });
});
