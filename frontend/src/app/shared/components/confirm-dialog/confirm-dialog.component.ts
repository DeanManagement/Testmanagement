import { Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { TranslateModule } from '@ngx-translate/core';

export interface ConfirmDialogData {
  titleKey: string;
  messageKey: string;
  messageParams?: object;
  /** Optional second line, e.g. an irreversibility warning. */
  secondaryMessageKey?: string;
  /** Renders the confirm button with the warn color for destructive actions. */
  danger?: boolean;
}

/**
 * Shared Material confirmation dialog replacing native `confirm()` calls
 * (PRD-022 §4.3). Resolves `true` when the user confirms, otherwise
 * `undefined` (Esc / cancel / backdrop click).
 */
@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, TranslateModule],
  template: `
    <h2 mat-dialog-title>{{ data.titleKey | translate }}</h2>
    <mat-dialog-content>
      <p>{{ data.messageKey | translate: data.messageParams }}</p>
      @if (data.secondaryMessageKey) {
        <p>{{ data.secondaryMessageKey | translate }}</p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close data-test-id="confirm-dialog-cancel">
        {{ 'common.cancel' | translate }}
      </button>
      <button mat-flat-button [color]="data.danger ? 'warn' : 'primary'" [mat-dialog-close]="true"
              cdkFocusInitial data-test-id="confirm-dialog-confirm">
        {{ 'common.confirm' | translate }}
      </button>
    </mat-dialog-actions>
  `,
})
export class ConfirmDialogComponent {
  readonly data = inject<ConfirmDialogData>(MAT_DIALOG_DATA);
}
