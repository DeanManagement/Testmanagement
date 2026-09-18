import { CustomField, UpdateCustomFieldRequest } from '../../shared/models/custom-field.model';

/** One row of the options editor; `original` is the stored label, null for a row added just now. */
export interface OptionRow {
  original: string | null;
  label: string;
}

export function toOptionRows(options: readonly string[]): OptionRow[] {
  return options.map((option) => ({ original: option, label: option }));
}

/**
 * The options part of an update. An edited row is a rename, so stored values follow it; a removed
 * row is a removal, which the server refuses while the option is in use (PRD-035 §4).
 */
export function optionChanges(rows: readonly OptionRow[]): Pick<UpdateCustomFieldRequest, 'options' | 'renamedOptions'> {
  const kept = rows.map((row) => ({ ...row, label: row.label.trim() })).filter((row) => row.label !== '');
  const renamedOptions: Record<string, string> = {};
  for (const row of kept) {
    if (row.original !== null && row.original !== row.label) {
      renamedOptions[row.original] = row.label;
    }
  }
  return { options: kept.map((row) => row.label), renamedOptions };
}

/** A translation key saying why the rows can't be saved, or null when they can. */
export function optionsError(rows: readonly OptionRow[], maxOptions: number): string | null {
  const labels = rows.map((row) => row.label.trim().toLowerCase()).filter((label) => label !== '');
  if (labels.length === 0) {
    return 'customField.dialog.optionsRequired';
  }
  if (labels.length > maxOptions) {
    return 'customField.dialog.tooManyOptions';
  }
  if (new Set(labels).size !== labels.length) {
    return 'customField.dialog.duplicateOption';
  }
  // ';' joins multi-select values in CSV import/export.
  return labels.some((label) => label.includes(';')) ? 'customField.dialog.optionSeparator' : null;
}

export interface OrderIndexChange {
  id: string;
  orderIndex: number;
}

/** After a drag each field's order index becomes its position; only the moved ones need saving. */
export function orderIndexChanges(ordered: readonly CustomField[]): OrderIndexChange[] {
  return ordered
    .map((field, index) => ({ id: field.id, orderIndex: index, previous: field.orderIndex }))
    .filter((change) => change.orderIndex !== change.previous)
    .map(({ id, orderIndex }) => ({ id, orderIndex }));
}
