import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule } from '@ngx-translate/core';
import { BugReportStatus } from '../../../shared/models/bug-report.model';

export interface ChangeBugStatusDialogData {
  currentStatus: BugReportStatus;
  newStatus: BugReportStatus;
}

@Component({
  selector: 'app-change-bug-status-dialog',
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
    <h2 mat-dialog-title>{{ 'bugReport.statusChange.title' | translate }}</h2>
    <mat-dialog-content>
      <p class="status-transition">
        {{ 'bugReport.status.' + data.currentStatus | translate }}
        &rarr;
        {{ 'bugReport.status.' + data.newStatus | translate }}
      </p>
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>{{ 'bugReport.statusChange.reason' | translate }}</mat-label>
        <textarea matInput [(ngModel)]="reason" required rows="3"></textarea>
        @if (!reason.trim()) {
          <mat-hint>{{ 'bugReport.statusChange.reasonRequired' | translate }}</mat-hint>
        }
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="onCancel()">{{ 'bugReport.statusChange.cancel' | translate }}</button>
      <button mat-flat-button color="primary" [disabled]="!reason.trim()" (click)="onConfirm()">
        {{ 'bugReport.statusChange.confirm' | translate }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .full-width { width: 100%; }
    mat-dialog-content { display: flex; flex-direction: column; gap: 8px; min-width: 400px; }
    .status-transition { font-weight: 500; margin: 0 0 8px; }
  `],
})
export class ChangeBugStatusDialogComponent {
  private readonly dialogRef = inject(MatDialogRef<ChangeBugStatusDialogComponent>);
  readonly data: ChangeBugStatusDialogData = inject(MAT_DIALOG_DATA);

  reason = '';

  onCancel(): void {
    this.dialogRef.close();
  }

  onConfirm(): void {
    this.dialogRef.close(this.reason.trim());
  }
}
