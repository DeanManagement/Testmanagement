import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule } from '@ngx-translate/core';

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
    <h2 mat-dialog-title>{{ 'testRun.reopen.title' | translate }}</h2>
    <mat-dialog-content>
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>{{ 'testRun.reopen.reason' | translate }}</mat-label>
        <textarea matInput [(ngModel)]="reason" required rows="3"></textarea>
        @if (!reason.trim()) {
          <mat-hint>{{ 'testRun.reopen.reasonRequired' | translate }}</mat-hint>
        }
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="onCancel()">{{ 'testRun.reopen.cancel' | translate }}</button>
      <button mat-flat-button color="primary" [disabled]="!reason.trim()" (click)="onConfirm()">
        {{ 'testRun.reopen.confirm' | translate }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .full-width { width: 100%; }
    mat-dialog-content { display: flex; flex-direction: column; gap: 8px; min-width: 400px; }
  `],
})
export class ReopenTestRunDialogComponent {
  private readonly dialogRef = inject(MatDialogRef<ReopenTestRunDialogComponent>);

  reason = '';

  onCancel(): void {
    this.dialogRef.close();
  }

  onConfirm(): void {
    this.dialogRef.close(this.reason.trim());
  }
}
