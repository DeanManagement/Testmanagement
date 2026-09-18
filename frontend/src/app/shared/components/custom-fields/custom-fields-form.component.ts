import { ChangeDetectorRef, Component, DestroyRef, OnChanges, forwardRef, inject, input } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  ControlValueAccessor,
  FormControl,
  FormRecord,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR,
  ReactiveFormsModule,
  ValidationErrors,
  Validator,
  Validators,
} from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { take } from 'rxjs/operators';
import { CustomFieldApiService } from '../../../core/services/custom-field-api.service';
import {
  CustomField,
  CustomFieldEntityType,
  CustomFieldValues,
  MAX_CUSTOM_FIELD_TEXT_LENGTH,
} from '../../models/custom-field.model';
import { FieldErrorComponent } from '../field-error/field-error.component';
import { CustomFieldControlValue, toControlValue, toRequestValues } from './custom-field-values';

/**
 * The project's custom fields for one entity type, as a single form control whose value is the
 * name-keyed map the API takes (PRD-035 §3.9). Archived fields are not offered, and their values
 * are left out of the map, so saving never touches them. Invalid while a required field is empty.
 * Field names are user data and are not translated.
 */
@Component({
  selector: 'app-custom-fields-form',
  standalone: true,
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatSelectModule, FieldErrorComponent],
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => CustomFieldsFormComponent), multi: true },
    { provide: NG_VALIDATORS, useExisting: forwardRef(() => CustomFieldsFormComponent), multi: true },
  ],
  template: `
    @if (fields.length > 0) {
      <div class="custom-fields" [formGroup]="controls" data-test-id="custom-fields-form">
        @for (field of fields; track field.id) {
          <mat-form-field appearance="outline" class="custom-field">
            <mat-label>{{ field.name }}</mat-label>
            @switch (field.fieldType) {
              @case ('SELECT') {
                <mat-select [formControlName]="field.id" [required]="field.required"
                            [attr.data-test-id]="'custom-field-' + field.name">
                  @if (!field.required) {
                    <mat-option value="">-</mat-option>
                  }
                  @for (option of field.options; track option) {
                    <mat-option [value]="option">{{ option }}</mat-option>
                  }
                </mat-select>
              }
              @case ('MULTI_SELECT') {
                <mat-select multiple [formControlName]="field.id" [required]="field.required"
                            [attr.data-test-id]="'custom-field-' + field.name">
                  @for (option of field.options; track option) {
                    <mat-option [value]="option">{{ option }}</mat-option>
                  }
                </mat-select>
              }
              @case ('NUMBER') {
                <input matInput type="number" step="any" [formControlName]="field.id" [required]="field.required"
                       [attr.data-test-id]="'custom-field-' + field.name" />
              }
              @case ('DATE') {
                <input matInput type="date" [formControlName]="field.id" [required]="field.required"
                       [attr.data-test-id]="'custom-field-' + field.name" />
              }
              @default {
                <input matInput [formControlName]="field.id" [required]="field.required"
                       [attr.data-test-id]="'custom-field-' + field.name" />
              }
            }
            <mat-error><app-field-error [control]="controls.controls[field.id]" /></mat-error>
          </mat-form-field>
        }
      </div>
    }
  `,
  styles: [`
    :host { display: block; }
    .custom-fields { display: grid; grid-template-columns: repeat(auto-fill, minmax(16rem, 1fr)); gap: 0 1rem; margin-top: 1rem; }
    .custom-field { width: 100%; }
  `],
})
export class CustomFieldsFormComponent implements ControlValueAccessor, Validator, OnChanges {
  private readonly api = inject(CustomFieldApiService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  readonly projectId = input.required<string>();
  readonly entityType = input.required<CustomFieldEntityType>();

  fields: CustomField[] = [];
  readonly controls = new FormRecord<FormControl<CustomFieldControlValue>>({});
  private values: CustomFieldValues = {};
  private onChange: (value: CustomFieldValues) => void = () => undefined;
  private onValidatorChange: () => void = () => undefined;
  private onTouched: () => void = () => undefined;

  constructor() {
    this.controls.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.onTouched();
      this.onChange(toRequestValues(this.fields, this.controls.getRawValue()));
    });
  }

  ngOnChanges(): void {
    if (!this.projectId()) {
      return;
    }
    this.api.getActive(this.projectId(), this.entityType()).pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe((fields) => {
        this.fields = fields;
        this.rebuildControls();
      });
  }

  writeValue(values: CustomFieldValues | null): void {
    this.values = values ?? {};
    this.rebuildControls();
  }

  registerOnChange(fn: (value: CustomFieldValues) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  registerOnValidatorChange(fn: () => void): void {
    this.onValidatorChange = fn;
  }

  setDisabledState(disabled: boolean): void {
    if (disabled) {
      this.controls.disable({ emitEvent: false });
    } else {
      this.controls.enable({ emitEvent: false });
    }
  }

  validate(_: AbstractControl): ValidationErrors | null {
    return this.controls.invalid ? { customFields: true } : null;
  }

  /** Runs when either the definitions or the values arrive; whichever is last completes the form. */
  private rebuildControls(): void {
    for (const name of Object.keys(this.controls.controls)) {
      this.controls.removeControl(name, { emitEvent: false });
    }
    for (const field of this.fields) {
      const validators = [
        ...(field.required ? [Validators.required] : []),
        ...(field.fieldType === 'TEXT' ? [Validators.maxLength(MAX_CUSTOM_FIELD_TEXT_LENGTH)] : []),
      ];
      this.controls.addControl(field.id,
        new FormControl<CustomFieldControlValue>(toControlValue(field, this.values[field.name]), validators),
        { emitEvent: false });
    }
    // The parent re-validates: a required field may have appeared after the parent form was built.
    this.onValidatorChange();
    this.cdr.markForCheck();
  }
}
