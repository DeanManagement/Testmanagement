import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule } from '@ngx-translate/core';

export interface CloneTestRunDialogData {
  name: string;
  environment: string;
}

export interface CloneTestRunDialogResult {
  name: string;
  environment?: string;
}

@Component({
  selector: 'app-clone-test-run-dialog',
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
    <h2 mat-dialog-title>{{ 'testRun.cloneDialog.title' | translate }}</h2>
    <mat-dialog-content>
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>{{ 'testRun.cloneDialog.name' | translate }}</mat-label>
        <input matInput [(ngModel)]="name" required />
      </mat-form-field>
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>{{ 'testRun.cloneDialog.environment' | translate }}</mat-label>
        <input matInput [(ngModel)]="environment" />
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="onCancel()">{{ 'testRun.cloneDialog.cancel' | translate }}</button>
      <button mat-flat-button color="primary" [disabled]="!name.trim()" (click)="onConfirm()">
        {{ 'testRun.cloneDialog.confirm' | translate }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .full-width { width: 100%; }
    mat-dialog-content { display: flex; flex-direction: column; gap: 8px; min-width: 400px; }
  `],
})
export class CloneTestRunDialogComponent {
  private readonly dialogRef = inject(MatDialogRef<CloneTestRunDialogComponent>);
  private readonly data: CloneTestRunDialogData = inject(MAT_DIALOG_DATA);

  name = `Copy of ${this.data.name}`;
  environment = this.data.environment ?? '';

  onCancel(): void {
    this.dialogRef.close();
  }

  onConfirm(): void {
    const result: CloneTestRunDialogResult = {
      name: this.name.trim(),
      environment: this.environment.trim() || undefined,
    };
    this.dialogRef.close(result);
  }
}
