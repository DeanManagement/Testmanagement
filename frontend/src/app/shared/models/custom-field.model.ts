/** Custom fields (PRD-035): typed, per-project attributes on test cases, test runs and bug reports. */
export type CustomFieldEntityType = 'TEST_CASE' | 'TEST_RUN' | 'BUG_REPORT';
export type CustomFieldType = 'TEXT' | 'NUMBER' | 'DATE' | 'SELECT' | 'MULTI_SELECT';

export const CUSTOM_FIELD_ENTITY_TYPES: CustomFieldEntityType[] = ['TEST_CASE', 'TEST_RUN', 'BUG_REPORT'];
export const CUSTOM_FIELD_TYPES: CustomFieldType[] = ['TEXT', 'NUMBER', 'DATE', 'SELECT', 'MULTI_SELECT'];

/** Server-side limits, mirrored so the UI can say why a button is disabled. */
export const MAX_ACTIVE_CUSTOM_FIELDS = 20;
export const MAX_CUSTOM_FIELD_OPTIONS = 50;
export const MAX_CUSTOM_FIELD_TEXT_LENGTH = 500;

export interface CustomField {
  id: string;
  entityType: CustomFieldEntityType;
  name: string;
  fieldType: CustomFieldType;
  options: string[];
  required: boolean;
  archived: boolean;
  orderIndex: number;
}

export interface CreateCustomFieldRequest {
  entityType: CustomFieldEntityType;
  name: string;
  fieldType: CustomFieldType;
  options?: string[];
  required?: boolean;
}

/** Every field optional; omitted fields are left unchanged. renamedOptions maps old label to new. */
export interface UpdateCustomFieldRequest {
  name?: string;
  options?: string[];
  renamedOptions?: Record<string, string>;
  required?: boolean;
  orderIndex?: number;
}

/** A value as the API carries it: DATE is yyyy-MM-dd, MULTI_SELECT a list of options. */
export type CustomFieldValue = string | number | string[] | null;

/** Values keyed by field name. In a request, null clears a field and an absent key leaves it alone. */
export type CustomFieldValues = Record<string, CustomFieldValue>;
