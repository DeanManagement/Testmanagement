import { Component } from '@angular/core';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { TestCaseStatus } from '../../../shared/models/test-case.model';

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
          <mat-option value="DRAFT">{{ 'testCaseStatus.DRAFT' | translate }}</mat-option>
          <mat-option value="ACTIVE">{{ 'testCaseStatus.ACTIVE' | translate }}</mat-option>
          <mat-option value="DEPRECATED">{{ 'testCaseStatus.DEPRECATED' | translate }}</mat-option>
        </mat-select>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>{{ 'common.cancel' | translate }}</button>
      <button mat-flat-button [disabled]="!selectedStatus" (click)="confirm()">{{ 'common.confirm' | translate }}</button>
    </mat-dialog-actions>
  `,
  styles: [`.full-width { width: 100%; }`],
})
export class BulkStatusDialogComponent {
  selectedStatus: TestCaseStatus | null = null;

  constructor(private dialogRef: MatDialogRef<BulkStatusDialogComponent>) {}

  confirm(): void {
    if (this.selectedStatus) {
      this.dialogRef.close(this.selectedStatus);
    }
  }
}
