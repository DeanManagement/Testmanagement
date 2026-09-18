import { ChangeDetectorRef, Component, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule } from '@ngx-translate/core';
import { Observable } from 'rxjs';
import { take } from 'rxjs/operators';
import { CustomFieldApiService } from '../../core/services/custom-field-api.service';
import {
  CUSTOM_FIELD_TYPES,
  CustomField,
  CustomFieldEntityType,
  CustomFieldType,
  MAX_CUSTOM_FIELD_OPTIONS,
} from '../../shared/models/custom-field.model';
import { OptionRow, optionChanges, optionsError, toOptionRows } from './custom-field-options';

export interface CustomFieldDialogData {
  projectId: string;
  entityType: CustomFieldEntityType;
  /** Present when editing. */
  field?: CustomField;
}

/**
 * Add or edit one custom field (PRD-035 §3.9). It saves itself and stays open on a refusal, so
 * the server's reason (an option in use, a duplicate name) shows without losing the edits.
 * Closes with true once saved. The type is fixed after creation: the server refuses a change
 * once values exist, and "new field, archive the old one" is the supported path.
 */
@Component({
  selector: 'app-custom-field-dialog',
  standalone: true,
  imports: [
    FormsModule,
    MatButtonModule,
    MatCheckboxModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    TranslateModule,
  ],
  template: `
    <h2 mat-dialog-title>{{ (data.field ? 'customField.dialog.editTitle' : 'customField.dialog.addTitle') | translate }}</h2>
    <mat-dialog-content>
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>{{ 'customField.dialog.name' | translate }}</mat-label>
        <input matInput [(ngModel)]="name" maxlength="100" required cdkFocusInitial data-test-id="custom-field-name">
      </mat-form-field>

      <mat-form-field appearance="outline" class="full-width">
        <mat-label>{{ 'customField.dialog.type' | translate }}</mat-label>
        <mat-select [(ngModel)]="fieldType" [disabled]="!!data.field" data-test-id="custom-field-type">
          @for (type of types; track type) {
            <mat-option [value]="type">{{ 'customField.type.' + type | translate }}</mat-option>
          }
        </mat-select>
        @if (data.field) {
          <mat-hint>{{ 'customField.dialog.typeFixed' | translate }}</mat-hint>
        }
      </mat-form-field>

      @if (hasOptions) {
        <fieldset class="options">
          <legend>{{ 'customField.dialog.options' | translate }}</legend>
          @for (row of rows; track $index; let i = $index) {
            <div class="option-row">
              <mat-form-field appearance="outline" subscriptSizing="dynamic" class="option-field">
                <input matInput [(ngModel)]="row.label" maxlength="500"
                       [attr.aria-label]="'customField.dialog.optionLabel' | translate: { index: i + 1 }"
                       data-test-id="custom-field-option">
              </mat-form-field>
              <button mat-icon-button type="button" (click)="removeOption(i)"
                      [attr.aria-label]="'customField.dialog.removeOption' | translate" data-test-id="custom-field-option-remove">
                <mat-icon>close</mat-icon>
              </button>
            </div>
          }
          <button mat-button type="button" (click)="addOption()" [disabled]="rows.length >= maxOptions"
                  data-test-id="custom-field-option-add">
            <mat-icon>add</mat-icon>
            {{ 'customField.dialog.addOption' | translate }}
          </button>
          @if (data.field) {
            <p class="hint">{{ 'customField.dialog.renameHint' | translate }}</p>
          }
        </fieldset>
      }

      <mat-checkbox [(ngModel)]="required" data-test-id="custom-field-required">
        {{ 'customField.dialog.required' | translate }}
      </mat-checkbox>
      <p class="hint">{{ 'customField.dialog.requiredHint' | translate }}</p>

      @if (errorKey) {
        <p class="error" role="alert">{{ errorKey | translate: { max: maxOptions } }}</p>
      }
      @if (serverError) {
        <p class="error" role="alert" data-test-id="custom-field-server-error">{{ serverError }}</p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" (click)="dialogRef.close()">{{ 'common.cancel' | translate }}</button>
      <button mat-flat-button type="button" [disabled]="!name.trim() || saving" (click)="save()"
              data-test-id="custom-field-save">
        {{ 'common.save' | translate }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    mat-dialog-content { min-width: min(90vw, 460px); }
    .full-width { width: 100%; }
    .options { border: 1px solid var(--tm-border); border-radius: 8px; margin: 0 0 1rem; padding: 0.5rem 0.75rem; }
    .options legend { font-size: 0.8rem; color: var(--tm-text-secondary); padding: 0 0.25rem; }
    .option-row { display: flex; align-items: center; gap: 0.25rem; margin-bottom: 0.25rem; }
    .option-field { flex: 1; }
    .hint { font-size: 0.8rem; color: var(--tm-text-secondary); margin: 0.25rem 0 0.75rem; }
    .error { color: var(--tm-accent); margin: 0.5rem 0 0; }
  `],
})
export class CustomFieldDialogComponent {
  readonly dialogRef = inject(MatDialogRef<CustomFieldDialogComponent, boolean>);
  readonly data: CustomFieldDialogData = inject(MAT_DIALOG_DATA);
  private readonly api = inject(CustomFieldApiService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  readonly types = CUSTOM_FIELD_TYPES;
  readonly maxOptions = MAX_CUSTOM_FIELD_OPTIONS;

  name = this.data.field?.name ?? '';
  fieldType: CustomFieldType = this.data.field?.fieldType ?? 'TEXT';
  required = this.data.field?.required ?? false;
  rows: OptionRow[] = toOptionRows(this.data.field?.options ?? []);
  saving = false;
  errorKey: string | null = null;
  serverError: string | null = null;

  get hasOptions(): boolean {
    return this.fieldType === 'SELECT' || this.fieldType === 'MULTI_SELECT';
  }

  addOption(): void {
    this.rows.push({ original: null, label: '' });
  }

  removeOption(index: number): void {
    this.rows.splice(index, 1);
  }

  save(): void {
    this.serverError = null;
    this.errorKey = this.hasOptions ? optionsError(this.rows, this.maxOptions) : null;
    if (this.errorKey) {
      return;
    }
    this.saving = true;
    this.request().pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => this.dialogRef.close(true),
      error: (error: HttpErrorResponse) => {
        this.saving = false;
        this.serverError = error.error?.message ?? error.message;
        this.cdr.detectChanges();
      },
    });
  }

  private request(): Observable<CustomField> {
    const name = this.name.trim();
    const options = this.hasOptions ? optionChanges(this.rows) : {};
    if (this.data.field) {
      return this.api.update(this.data.projectId, this.data.field.id, { name, required: this.required, ...options });
    }
    return this.api.create(this.data.projectId, {
      entityType: this.data.entityType,
      name,
      fieldType: this.fieldType,
      required: this.required,
      options: options.options,
    });
  }
}
