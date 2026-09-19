import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule } from '@ngx-translate/core';

/** Which run status change the reason is for. */
export interface ReasonDialogData {
  action: 'reopen' | 'abort';
}

@Component({
  selector: 'app-reopen-test-run-dialog',
  standalone: true,
  imports: [
    FormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    TranslateModule,
  ],
  template: `
    <h2 mat-dialog-title>{{ keys + '.title' | translate }}</h2>
    <mat-dialog-content>
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>{{ keys + '.reason' | translate }}</mat-label>
        <textarea matInput [(ngModel)]="reason" required rows="3"></textarea>
        @if (!reason.trim()) {
          <mat-hint>{{ keys + '.reasonRequired' | translate }}</mat-hint>
        }
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="onCancel()">{{ keys + '.cancel' | translate }}</button>
      <button mat-flat-button color="primary" [disabled]="!reason.trim()" (click)="onConfirm()">
        {{ keys + '.confirm' | translate }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .full-width { width: 100%; }
    mat-dialog-content { display: flex; flex-direction: column; gap: 8px; min-width: min(90vw, 400px); }
  `],
})
export class ReopenTestRunDialogComponent {
  private readonly dialogRef = inject(MatDialogRef<ReopenTestRunDialogComponent>);
  private readonly data = inject<ReasonDialogData | null>(MAT_DIALOG_DATA, { optional: true });

  /** The same prompt serves reopening (default) and aborting; both need a reason. */
  readonly keys = this.data?.action === 'abort' ? 'testRun.abortDialog' : 'testRun.reopen';

  reason = '';

  onCancel(): void {
    this.dialogRef.close();
  }

  onConfirm(): void {
    this.dialogRef.close(this.reason.trim());
  }
}
