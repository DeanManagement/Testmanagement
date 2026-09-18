import { ParamMap } from '@angular/router';
import { CustomField } from '../../models/custom-field.model';

/** List endpoints take `cf.<name>=v` (repeatable) and `cf.<name>.min` / `.max` (PRD-035 §3.5). */
export const CUSTOM_FIELD_PARAM_PREFIX = 'cf.';

/** What the filter panel holds for one field; which parts apply depends on the field's type. */
export interface CustomFieldFilterState {
  /** SELECT and MULTI_SELECT: the chosen options, any of which matches. */
  options: string[];
  /** TEXT: a substring. */
  text: string;
  /** NUMBER and DATE: inclusive bounds. */
  min: string;
  max: string;
}

export function isRangeField(field: CustomField): boolean {
  return field.fieldType === 'NUMBER' || field.fieldType === 'DATE';
}

export function hasOptions(field: CustomField): boolean {
  return field.fieldType === 'SELECT' || field.fieldType === 'MULTI_SELECT';
}

/** Every `cf.*` parameter of the URL, to pass through to the API untouched. */
export function readCustomFieldParams(params: ParamMap): Record<string, string[]> {
  const result: Record<string, string[]> = {};
  for (const key of params.keys) {
    if (key.startsWith(CUSTOM_FIELD_PARAM_PREFIX)) {
      result[key] = params.getAll(key);
    }
  }
  return result;
}

export function readFilterState(field: CustomField, params: ParamMap): CustomFieldFilterState {
  const name = CUSTOM_FIELD_PARAM_PREFIX + field.name;
  return {
    options: hasOptions(field) ? params.getAll(name) : [],
    text: field.fieldType === 'TEXT' ? (params.get(name) ?? '') : '',
    min: isRangeField(field) ? (params.get(`${name}.min`) ?? '') : '',
    max: isRangeField(field) ? (params.get(`${name}.max`) ?? '') : '',
  };
}

/** The query parameters one field's state becomes; null removes a parameter on a merge-navigate. */
export function toQueryParams(field: CustomField, state: CustomFieldFilterState): Record<string, string | string[] | null> {
  const name = CUSTOM_FIELD_PARAM_PREFIX + field.name;
  if (isRangeField(field)) {
    return { [`${name}.min`]: state.min || null, [`${name}.max`]: state.max || null };
  }
  if (hasOptions(field)) {
    return { [name]: state.options.length > 0 ? state.options : null };
  }
  return { [name]: state.text.trim() || null };
}

export function isActive(state: CustomFieldFilterState): boolean {
  return state.options.length > 0 || state.text.trim() !== '' || state.min !== '' || state.max !== '';
}
