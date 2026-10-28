import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule } from '@ngx-translate/core';
import { take } from 'rxjs/operators';
import { CustomFieldApiService } from '../../../core/services/custom-field-api.service';
import { CustomField } from '../../../shared/models/custom-field.model';
import { BulkEditTestCasesChanges, Priority } from '../../../shared/models/test-case.model';

export interface BulkEditFolderOption {
  id: string;
  name: string;
  level: number;
}

export interface BulkEditDialogData {
  projectId: string;
  count: number;
  folders: BulkEditFolderOption[];
}

/** The folder choice: leave as is, the project root, or a folder id. */
export const KEEP_FOLDER = '';
export const ROOT_FOLDER = '__root__';

export interface BulkEditForm {
  priority: Priority | '';
  addLabels: string;
  removeLabels: string;
  folder: string;
  customFieldName: string;
  customFieldValue: string;
}

/**
 * The changes the form asks for, or null when it asks for none. Empty parts are left out, so the
 * server leaves those fields alone; a chosen field with an empty value clears it.
 */
export function toBulkEditChanges(form: BulkEditForm): BulkEditTestCasesChanges | null {
  const changes: BulkEditTestCasesChanges = {};
  if (form.priority) changes.priority = form.priority;
  const addLabels = splitLabels(form.addLabels);
  const removeLabels = splitLabels(form.removeLabels);
  if (addLabels.length) changes.addLabels = addLabels;
  if (removeLabels.length) changes.removeLabels = removeLabels;
  if (form.folder !== KEEP_FOLDER) {
    changes.moveToFolder = true;
    changes.folderId = form.folder === ROOT_FOLDER ? null : form.folder;
  }
  if (form.customFieldName) {
    changes.customFields = { [form.customFieldName]: form.customFieldValue.trim() || null };
  }
  return Object.keys(changes).length ? changes : null;
}

function splitLabels(value: string): string[] {
  return value.split(',').map((label) => label.trim()).filter((label) => label);
}

/** TM11: one dialog for priority, labels, folder and a custom field across the selected cases. */
@Component({
  selector: 'app-bulk-edit-dialog',
  standalone: true,
  imports: [FormsModule, MatButtonModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    TranslateModule],
  template: `
    <h2 mat-dialog-title>{{ 'bulk.edit.title' | translate: { count: data.count } }}</h2>
    <mat-dialog-content class="bulk-edit-form">
      <p class="hint">{{ 'bulk.edit.hint' | translate }}</p>
      <mat-form-field appearance="outline">
        <mat-label>{{ 'testCase.form.priority' | translate }}</mat-label>
        <mat-select [(ngModel)]="form.priority" data-test-id="bulk-edit-priority">
          <mat-option value="">{{ 'bulk.edit.unchanged' | translate }}</mat-option>
          @for (priority of priorities; track priority) {
            <mat-option [value]="priority">{{ 'priority.' + priority | translate }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>{{ 'bulk.edit.addLabels' | translate }}</mat-label>
        <input matInput [(ngModel)]="form.addLabels" data-test-id="bulk-edit-add-labels">
        <mat-hint>{{ 'testCase.form.labelsHint' | translate }}</mat-hint>
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>{{ 'bulk.edit.removeLabels' | translate }}</mat-label>
        <input matInput [(ngModel)]="form.removeLabels" data-test-id="bulk-edit-remove-labels">
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>{{ 'bulk.edit.folder' | translate }}</mat-label>
        <mat-select [(ngModel)]="form.folder" data-test-id="bulk-edit-folder">
          <mat-option [value]="keepFolder">{{ 'bulk.edit.unchanged' | translate }}</mat-option>
          <mat-option [value]="rootFolder">{{ 'bulk.edit.rootFolder' | translate }}</mat-option>
          @for (folder of data.folders; track folder.id) {
            <mat-option [value]="folder.id">{{ '  '.repeat(folder.level) }}{{ folder.name }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      @if (customFields().length) {
        <div class="custom-field-row">
          <mat-form-field appearance="outline">
            <mat-label>{{ 'bulk.edit.customField' | translate }}</mat-label>
            <mat-select [(ngModel)]="form.customFieldName" data-test-id="bulk-edit-custom-field">
              <mat-option value="">{{ 'bulk.edit.unchanged' | translate }}</mat-option>
              @for (field of customFields(); track field.id) {
                <mat-option [value]="field.name">{{ field.name }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
          @if (form.customFieldName) {
            <mat-form-field appearance="outline">
              <mat-label>{{ 'bulk.edit.value' | translate }}</mat-label>
              @if (selectedField()?.options?.length) {
                <mat-select [(ngModel)]="form.customFieldValue" data-test-id="bulk-edit-custom-value">
                  <mat-option value="">{{ 'bulk.edit.clear' | translate }}</mat-option>
                  @for (option of selectedField()!.options; track option) {
                    <mat-option [value]="option">{{ option }}</mat-option>
                  }
                </mat-select>
              } @else {
                <input matInput [(ngModel)]="form.customFieldValue" data-test-id="bulk-edit-custom-value">
              }
              @if (!selectedField()?.options?.length) {
                <mat-hint>{{ 'bulk.edit.emptyClears' | translate }}</mat-hint>
              }
            </mat-form-field>
          }
        </div>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>{{ 'common.cancel' | translate }}</button>
      <button mat-flat-button [disabled]="!changes" (click)="apply()" data-test-id="bulk-edit-apply">
        {{ 'bulk.edit.apply' | translate }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .bulk-edit-form { display: flex; flex-direction: column; gap: 4px; min-width: min(90vw, 460px); }
    .hint { color: var(--tm-text-secondary); margin-top: 0; }
    .custom-field-row { display: flex; gap: 8px; flex-wrap: wrap; }
    .custom-field-row mat-form-field { flex: 1 1 180px; }
  `],
})
export class BulkEditDialogComponent implements OnInit {
  readonly data = inject<BulkEditDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<BulkEditDialogComponent, BulkEditTestCasesChanges>);
  private readonly customFieldApi = inject(CustomFieldApiService);

  readonly priorities: Priority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
  readonly keepFolder = KEEP_FOLDER;
  readonly rootFolder = ROOT_FOLDER;
  readonly customFields = signal<CustomField[]>([]);

  form: BulkEditForm = {
    priority: '', addLabels: '', removeLabels: '', folder: KEEP_FOLDER, customFieldName: '', customFieldValue: '',
  };

  ngOnInit(): void {
    this.customFieldApi.getActive(this.data.projectId, 'TEST_CASE').pipe(take(1))
      .subscribe({ next: (fields) => this.customFields.set(fields), error: () => undefined });
  }

  selectedField(): CustomField | undefined {
    return this.customFields().find((field) => field.name === this.form.customFieldName);
  }

  get changes(): BulkEditTestCasesChanges | null {
    return toBulkEditChanges(this.form);
  }

  apply(): void {
    const changes = this.changes;
    if (changes) {
      this.dialogRef.close(changes);
    }
  }
}
