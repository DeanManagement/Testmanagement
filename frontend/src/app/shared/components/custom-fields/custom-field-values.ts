import { CustomField, CustomFieldValue, CustomFieldValues } from '../../models/custom-field.model';

/** What a form control holds for a field: text for most types, a number or a list for the others. */
export type CustomFieldControlValue = string | number | string[] | null;

/** The control value a stored value starts as; an absent value becomes that type's "empty". */
export function toControlValue(field: CustomField, value: CustomFieldValue | undefined): CustomFieldControlValue {
  if (field.fieldType === 'MULTI_SELECT') {
    return Array.isArray(value) ? value : [];
  }
  if (field.fieldType === 'NUMBER') {
    return typeof value === 'number' ? value : null;
  }
  return typeof value === 'string' ? value : '';
}

export function isEmptyValue(value: CustomFieldControlValue | undefined): boolean {
  return value == null || value === '' || (Array.isArray(value) && value.length === 0);
}

/**
 * The name-keyed map a request carries. Every given field is present and an empty one is null,
 * because to the server an absent key means "leave alone" and null means "clear".
 */
export function toRequestValues(
  fields: readonly CustomField[],
  controlValues: Readonly<Record<string, CustomFieldControlValue>>,
): CustomFieldValues {
  const values: CustomFieldValues = {};
  for (const field of fields) {
    const value = controlValues[field.id];
    const trimmed = typeof value === 'string' ? value.trim() : value;
    values[field.name] = isEmptyValue(trimmed) ? null : trimmed;
  }
  return values;
}

/** How a value reads on a detail page; a multi-select lists its options. */
export function displayValue(value: CustomFieldValue): string {
  if (value == null) {
    return '';
  }
  return Array.isArray(value) ? value.join(', ') : String(value);
}
