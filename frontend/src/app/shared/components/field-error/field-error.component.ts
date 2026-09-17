import { Component, input } from '@angular/core';
import { AbstractControl } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';

/**
 * The sentence that explains why a form field is invalid. Goes inside a `<mat-error>`, which
 * already decides *when* to show it (invalid and touched) and marks the input `aria-invalid`.
 *
 * Until this existed no form in the app had a `<mat-error>` at all: a required field left empty
 * turned red and disabled Save, and said nothing — colour as the only signal (WCAG 1.4.1, 3.3.1).
 */
@Component({
  selector: 'app-field-error',
  standalone: true,
  imports: [TranslateModule],
  template: `
    @if (control().hasError('required')) {
      {{ 'validation.required' | translate }}
    } @else if (control().hasError('maxlength')) {
      {{ 'validation.maxLength' | translate: { max: control().getError('maxlength').requiredLength } }}
    }
  `,
})
export class FieldErrorComponent {
  readonly control = input.required<AbstractControl>();
}
