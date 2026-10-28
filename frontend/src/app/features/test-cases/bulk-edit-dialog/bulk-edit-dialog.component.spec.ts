import { describe, expect, it } from 'vitest';
import { BulkEditForm, KEEP_FOLDER, ROOT_FOLDER, toBulkEditChanges } from './bulk-edit-dialog.component';

const untouched: BulkEditForm = {
  priority: '', addLabels: '', removeLabels: '', folder: KEEP_FOLDER, customFieldName: '', customFieldValue: '',
};

/** TM11: the dialog sends only what the tester changed. */
describe('toBulkEditChanges', () => {
  it('asks for nothing when nothing was changed', () => {
    expect(toBulkEditChanges(untouched)).toBeNull();
  });

  it('sends the priority and the labels split on commas', () => {
    const changes = toBulkEditChanges({ ...untouched, priority: 'HIGH', addLabels: 'smoke, regression ,', removeLabels: 'old' });

    expect(changes).toEqual({ priority: 'HIGH', addLabels: ['smoke', 'regression'], removeLabels: ['old'] });
  });

  it('moves to a folder, or to the root', () => {
    expect(toBulkEditChanges({ ...untouched, folder: 'f1' })).toEqual({ moveToFolder: true, folderId: 'f1' });
    expect(toBulkEditChanges({ ...untouched, folder: ROOT_FOLDER })).toEqual({ moveToFolder: true, folderId: null });
  });

  it('sets a custom field, and clears it when the value is empty', () => {
    expect(toBulkEditChanges({ ...untouched, customFieldName: 'Component', customFieldValue: ' Payments ' }))
      .toEqual({ customFields: { Component: 'Payments' } });
    expect(toBulkEditChanges({ ...untouched, customFieldName: 'Component', customFieldValue: '' }))
      .toEqual({ customFields: { Component: null } });
  });
});
