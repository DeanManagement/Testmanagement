import { ChangeDetectorRef, Component, DestroyRef, OnChanges, forwardRef, inject, input } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ControlValueAccessor, FormsModule, NG_VALUE_ACCESSOR } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule } from '@ngx-translate/core';
import { take } from 'rxjs/operators';
import { EnvironmentApiService } from '../../../core/services/environment-api.service';
import { EnvironmentOptions, environmentOptions } from './environment-options';

/**
 * Environment name with suggestions from the project's catalogue (PRD-032). The value stays a
 * plain name: the server matches it ignoring case and registers a new one, shown here as "Add".
 * Works with both formControlName and ngModel.
 */
@Component({
  selector: 'app-environment-input',
  standalone: true,
  imports: [FormsModule, MatAutocompleteModule, MatFormFieldModule, MatInputModule, TranslateModule],
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => EnvironmentInputComponent), multi: true }],
  template: `
    <mat-form-field appearance="outline" class="environment-field">
      <mat-label>{{ labelKey() | translate }}</mat-label>
      <input matInput
             [ngModel]="value"
             (ngModelChange)="onInput($event)"
             (blur)="onTouched()"
             [disabled]="disabled"
             [matAutocomplete]="auto"
             [attr.data-test-id]="testId()" />
      <mat-autocomplete #auto="matAutocomplete" (optionSelected)="onInput($event.option.value)">
        @for (name of options.matches; track name) {
          <mat-option [value]="name">{{ name }}</mat-option>
        }
        @if (options.newName; as newName) {
          <mat-option [value]="newName" data-test-id="environment-add-option">
            {{ 'environment.addNew' | translate: { name: newName } }}
          </mat-option>
        }
      </mat-autocomplete>
    </mat-form-field>
  `,
  styles: [':host { display: block; } .environment-field { width: 100%; }'],
})
export class EnvironmentInputComponent implements ControlValueAccessor, OnChanges {
  private readonly api = inject(EnvironmentApiService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  readonly projectId = input.required<string>();
  readonly labelKey = input('environment.label');
  readonly testId = input('environment-input');

  value = '';
  disabled = false;
  options: EnvironmentOptions = { matches: [], newName: null };
  private names: string[] = [];
  private onChange: (value: string) => void = () => undefined;
  onTouched: () => void = () => undefined;

  ngOnChanges(): void {
    if (!this.projectId()) {
      return;
    }
    this.api.getActive(this.projectId()).pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (environments) => {
        this.names = environments.map((environment) => environment.name);
        this.refreshOptions();
      },
      // Suggestions are a convenience: typing a name still works without them.
      error: () => this.refreshOptions(),
    });
  }

  onInput(value: string): void {
    this.value = value ?? '';
    this.refreshOptions();
    this.onChange(this.value);
  }

  writeValue(value: string | null): void {
    this.value = value ?? '';
    this.refreshOptions();
  }

  registerOnChange(fn: (value: string) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(disabled: boolean): void {
    this.disabled = disabled;
  }

  private refreshOptions(): void {
    this.options = environmentOptions(this.names, this.value);
    this.cdr.markForCheck();
  }
}
