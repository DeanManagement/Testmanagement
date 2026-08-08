import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule } from '@ngx-translate/core';
import { ProjectWorkflow, TriggerPipelineRequest } from '../../../shared/models/build-server.model';

export interface TriggerPipelineDialogData {
  workflow: ProjectWorkflow;
}

/**
 * Pre-filled trigger dialog (PRD-024 §3.5): ref and parameters default from the workflow
 * definition and can be overridden per run. Parameters are edited as KEY=value lines.
 */
@Component({
  selector: 'app-trigger-pipeline-dialog',
  standalone: true,
  imports: [
    FormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    TranslateModule,
  ],
  template: `
    <h2 mat-dialog-title data-test-id="trigger-dialog-title">
      {{ 'automation.trigger.title' | translate: { name: data.workflow.name } }}
    </h2>
    <mat-dialog-content class="trigger-dialog-content">
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>{{ 'automation.trigger.ref' | translate }}</mat-label>
        <input matInput [(ngModel)]="ref" data-test-id="trigger-ref-input">
        <mat-hint>{{ 'automation.trigger.refHint' | translate }}</mat-hint>
      </mat-form-field>
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>{{ 'automation.trigger.parameters' | translate }}</mat-label>
        <textarea matInput rows="5" [(ngModel)]="parametersText"
                  data-test-id="trigger-parameters-input"></textarea>
        <mat-hint>{{ 'automation.trigger.parametersHint' | translate }}</mat-hint>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close data-test-id="trigger-cancel-btn">
        {{ 'common.cancel' | translate }}
      </button>
      <button mat-flat-button (click)="confirm()" data-test-id="trigger-confirm-btn">
        {{ 'automation.trigger.run' | translate }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .trigger-dialog-content { display: flex; flex-direction: column; gap: 4px; min-width: 380px; }
    .full-width { width: 100%; }
  `],
})
export class TriggerPipelineDialogComponent {
  readonly data = inject<TriggerPipelineDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<TriggerPipelineDialogComponent>);

  ref = this.data.workflow.defaultRef ?? '';
  parametersText = Object.entries(this.data.workflow.defaultParameters ?? {})
    .map(([key, value]) => `${key}=${value}`)
    .join('\n');

  confirm(): void {
    const parameters: Record<string, string> = {};
    for (const line of this.parametersText.split('\n')) {
      const trimmed = line.trim();
      if (!trimmed) {
        continue;
      }
      const separator = trimmed.indexOf('=');
      if (separator > 0) {
        parameters[trimmed.slice(0, separator).trim()] = trimmed.slice(separator + 1).trim();
      }
    }
    const request: TriggerPipelineRequest = {
      ref: this.ref.trim() || null,
      parameters,
    };
    this.dialogRef.close(request);
  }
}
