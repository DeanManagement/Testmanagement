import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule } from '@ngx-translate/core';
import { ProjectEnvironment } from '../../shared/models/environment.model';

export interface MergeEnvironmentDialogData {
  source: ProjectEnvironment;
  candidates: ProjectEnvironment[];
}

/** Picks the environment to merge into; closes with its id. */
@Component({
  selector: 'app-merge-environment-dialog',
  standalone: true,
  imports: [FormsModule, MatButtonModule, MatDialogModule, MatFormFieldModule, MatSelectModule, TranslateModule],
  template: `
    <h2 mat-dialog-title id="merge-environment-title">
      {{ 'environment.merge.title' | translate: { name: data.source.name } }}
    </h2>
    <mat-dialog-content aria-labelledby="merge-environment-title">
      <p>{{ 'environment.merge.explanation' | translate: { name: data.source.name, runs: data.source.runCount, bugs: data.source.bugCount } }}</p>
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>{{ 'environment.merge.target' | translate }}</mat-label>
        <mat-select [(ngModel)]="targetId" data-test-id="merge-environment-target">
          @for (candidate of data.candidates; track candidate.id) {
            <mat-option [value]="candidate.id">{{ candidate.name }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="dialogRef.close()">{{ 'common.cancel' | translate }}</button>
      <button mat-flat-button color="warn" [disabled]="!targetId" (click)="dialogRef.close(targetId)"
              data-test-id="merge-environment-confirm">
        {{ 'environment.merge.confirm' | translate }}
      </button>
    </mat-dialog-actions>
  `,
  styles: ['.full-width { width: 100%; } mat-dialog-content { min-width: min(90vw, 420px); }'],
})
export class MergeEnvironmentDialogComponent {
  readonly dialogRef = inject(MatDialogRef<MergeEnvironmentDialogComponent, string>);
  readonly data: MergeEnvironmentDialogData = inject(MAT_DIALOG_DATA);
  targetId = '';
}
