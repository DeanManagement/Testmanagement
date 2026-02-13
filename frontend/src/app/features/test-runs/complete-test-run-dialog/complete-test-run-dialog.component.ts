import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { CompletionInfo } from '../../../shared/models/test-run.model';

@Component({
  selector: 'app-complete-test-run-dialog',
  standalone: true,
  imports: [
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    TranslateModule,
  ],
  template: `
    <h2 mat-dialog-title>{{ 'testRun.complete.title' | translate }}</h2>
    <mat-dialog-content>
      <div class="stats">
        <div class="stat-row">
          <span class="stat-label">{{ 'report.total' | translate }}</span>
          <span class="stat-value">{{ data.total }}</span>
        </div>
        <div class="stat-row stat--passed">
          <span class="stat-label">{{ 'report.passed' | translate }}</span>
          <span class="stat-value">{{ data.passed }}</span>
        </div>
        @if (data.failed > 0) {
          <div class="stat-row stat--failed">
            <span class="stat-label">{{ 'report.failed' | translate }}</span>
            <span class="stat-value">{{ data.failed }}</span>
          </div>
        }
        @if (data.blocked > 0) {
          <div class="stat-row stat--blocked">
            <span class="stat-label">{{ 'report.blocked' | translate }}</span>
            <span class="stat-value">{{ data.blocked }}</span>
          </div>
        }
        @if (data.skipped > 0) {
          <div class="stat-row stat--skipped">
            <span class="stat-label">{{ 'report.skipped' | translate }}</span>
            <span class="stat-value">{{ data.skipped }}</span>
          </div>
        }
        @if (data.pending > 0) {
          <div class="stat-row stat--pending">
            <span class="stat-label">{{ 'report.pending' | translate }}</span>
            <span class="stat-value">{{ data.pending }}</span>
          </div>
        }
      </div>

      @if (allPassed) {
        <div class="message message--success">
          <mat-icon>check_circle</mat-icon>
          <span>{{ 'testRun.complete.allPassed' | translate }}</span>
        </div>
      } @else {
        <div class="message message--warning">
          <mat-icon>warning</mat-icon>
          <span>{{ 'testRun.complete.warning' | translate:{ failed: data.failed, blocked: data.blocked, worstStatus: data.worstStatus } }}</span>
        </div>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="onCancel()">{{ 'testRun.complete.cancel' | translate }}</button>
      <button mat-flat-button color="primary" (click)="onConfirm()">
        {{ 'testRun.complete.confirm' | translate }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    mat-dialog-content { min-width: 400px; }
    .stats { display: flex; flex-direction: column; gap: 4px; margin-bottom: 16px; }
    .stat-row { display: flex; justify-content: space-between; padding: 4px 8px; border-radius: 4px; }
    .stat--passed { color: #2e7d32; }
    .stat--failed { color: #c62828; }
    .stat--blocked { color: #e65100; }
    .stat--skipped { color: #546e7a; }
    .stat--pending { color: #f9a825; }
    .stat-label { font-weight: 500; }
    .message { display: flex; align-items: center; gap: 8px; padding: 12px; border-radius: 8px; }
    .message--success { background: #e8f5e9; color: #2e7d32; }
    .message--warning { background: #fff3e0; color: #e65100; }
  `],
})
export class CompleteTestRunDialogComponent {
  private readonly dialogRef = inject(MatDialogRef<CompleteTestRunDialogComponent>);
  readonly data: CompletionInfo = inject(MAT_DIALOG_DATA);

  get allPassed(): boolean {
    return this.data.failed === 0 && this.data.blocked === 0 && this.data.skipped === 0 && this.data.pending === 0;
  }

  onCancel(): void {
    this.dialogRef.close(false);
  }

  onConfirm(): void {
    this.dialogRef.close(true);
  }
}
