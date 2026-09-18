import { Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { TestCaseStatus } from '../../../shared/models/test-case.model';
import { selectableStatuses } from '../review/review-status';

export interface BulkStatusDialogData {
  /** PRD-033: ACTIVE is set only by approving, so it isn't offered. */
  reviewRequired: boolean;
}

@Component({
  selector: 'app-bulk-status-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, MatSelectModule, MatFormFieldModule, FormsModule, TranslateModule],
  template: `
    <h2 mat-dialog-title>{{ 'bulk.updateStatus' | translate }}</h2>
    <mat-dialog-content>
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>{{ 'bulk.selectStatus' | translate }}</mat-label>
        <mat-select [(ngModel)]="selectedStatus">
          @for (status of statuses; track status) {
            <mat-option [value]="status">{{ 'testCaseStatus.' + status | translate }}</mat-option>
          }
        </mat-select>
        @if (data?.reviewRequired) {
          <mat-hint>{{ 'review.bulkHint' | translate }}</mat-hint>
        }
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>{{ 'common.cancel' | translate }}</button>
      <button mat-flat-button [disabled]="!selectedStatus" (click)="confirm()">{{ 'common.confirm' | translate }}</button>
    </mat-dialog-actions>
  `,
  styles: [`.full-width { width: 100%; } mat-dialog-content { min-width: min(90vw, 400px); }`],
})
export class BulkStatusDialogComponent {
  readonly data = inject<BulkStatusDialogData | null>(MAT_DIALOG_DATA, { optional: true });
  readonly statuses = selectableStatuses(this.data?.reviewRequired ?? false);
  selectedStatus: TestCaseStatus | null = null;

  constructor(private dialogRef: MatDialogRef<BulkStatusDialogComponent>) {}

  confirm(): void {
    if (this.selectedStatus) {
      this.dialogRef.close(this.selectedStatus);
    }
  }
}
