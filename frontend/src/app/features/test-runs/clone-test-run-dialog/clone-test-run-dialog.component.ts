import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule } from '@ngx-translate/core';
import { EnvironmentInputComponent } from '../../../shared/components/environment-input/environment-input.component';

export interface CloneTestRunDialogData {
  projectId: string;
  name: string;
  environment: string;
}

export interface CloneTestRunDialogResult {
  name: string;
  /** Always sent: prefilled with the source's, so "" means the admin cleared it (PRD-032). */
  environment: string;
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
    EnvironmentInputComponent,
  ],
  template: `
    <h2 mat-dialog-title id="clone-test-run-dialog-title">{{ 'testRun.cloneDialog.title' | translate }}</h2>
    <mat-dialog-content role="dialog" aria-labelledby="clone-test-run-dialog-title">
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>{{ 'testRun.cloneDialog.name' | translate }}</mat-label>
        <input matInput [(ngModel)]="name" required aria-required="true" />
      </mat-form-field>
      <app-environment-input [projectId]="data.projectId" labelKey="testRun.cloneDialog.environment"
                             testId="clone-test-run-environment" [(ngModel)]="environment" />
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
    mat-dialog-content { display: flex; flex-direction: column; gap: 8px; min-width: min(90vw, 400px); }
  `],
})
export class CloneTestRunDialogComponent {
  private readonly dialogRef = inject(MatDialogRef<CloneTestRunDialogComponent>);
  readonly data: CloneTestRunDialogData = inject(MAT_DIALOG_DATA);

  name = `Copy of ${this.data.name}`;
  environment = this.data.environment ?? '';

  onCancel(): void {
    this.dialogRef.close();
  }

  onConfirm(): void {
    const result: CloneTestRunDialogResult = {
      name: this.name.trim(),
      environment: this.environment.trim(),
    };
    this.dialogRef.close(result);
  }
}
