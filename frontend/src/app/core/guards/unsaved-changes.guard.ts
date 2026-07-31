import { CanDeactivateFn } from '@angular/router';
import { inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { map } from 'rxjs/operators';
import { ConfirmDialogComponent, ConfirmDialogData } from '../../shared/components/confirm-dialog/confirm-dialog.component';

export interface HasUnsavedChanges {
  hasUnsavedChanges(): boolean;
}

export const unsavedChangesGuard: CanDeactivateFn<HasUnsavedChanges> = (component) => {
  if (component.hasUnsavedChanges && component.hasUnsavedChanges()) {
    const dialog = inject(MatDialog);
    return dialog
      .open(ConfirmDialogComponent, {
        data: {
          titleKey: 'common.confirm',
          messageKey: 'common.unsavedChangesWarning',
        } as ConfirmDialogData,
      })
      .afterClosed()
      .pipe(map((confirmed) => confirmed === true));
  }
  return true;
};
