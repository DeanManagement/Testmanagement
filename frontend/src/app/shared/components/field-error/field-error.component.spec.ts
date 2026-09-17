import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { describe, expect, it } from 'vitest';
import { FieldErrorComponent } from './field-error.component';

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, FieldErrorComponent],
  template: `
    <mat-form-field>
      <input matInput [formControl]="title" />
      <mat-error><app-field-error [control]="title" /></mat-error>
    </mat-form-field>
  `,
})
class HostComponent {
  readonly title = new FormControl('', [Validators.required, Validators.maxLength(5)]);
}

function render() {
  TestBed.configureTestingModule({
    imports: [HostComponent, TranslateModule.forRoot()],
    providers: [provideNoopAnimations()],
  });
  const translate = TestBed.inject(TranslateService);
  translate.setTranslation('en', { validation: { required: 'This field is required', maxLength: 'At most {{max}} characters' } });
  translate.use('en');
  const fixture = TestBed.createComponent(HostComponent);
  fixture.detectChanges();
  const element = fixture.nativeElement as HTMLElement;
  return {
    fixture,
    title: fixture.componentInstance.title,
    input: element.querySelector('input') as HTMLInputElement,
    message: () => element.querySelector('mat-error')?.textContent?.trim() ?? null,
  };
}

describe('FieldErrorComponent', () => {
  describe('when a required field has not been touched', () => {
    it('should stay quiet', () => {
      expect(render().message()).toBeNull();
    });
  });

  describe('when a required field is left empty', () => {
    it('should say that it is required', () => {
      const { fixture, title, message } = render();

      title.markAsTouched();
      fixture.detectChanges();

      expect(message()).toBe('This field is required');
    });

    // Material leaves aria-invalid unset for a field that is merely empty-and-required, on
    // purpose: it is announced as required, and the message is what explains the problem.
    it('should be announced as required, with the message linked to the input', () => {
      const { fixture, title, input } = render();

      title.markAsTouched();
      fixture.detectChanges();
      fixture.detectChanges();

      expect(input.getAttribute('aria-required')).toBe('true');
      const describedBy = input.getAttribute('aria-describedby') ?? '';
      const linked = describedBy.split(' ').map((id) => document.getElementById(id)?.textContent ?? '').join(' ');
      expect(linked).toContain('This field is required');
    });
  });

  describe('when the value is too long', () => {
    it('should name the limit', () => {
      const { fixture, title, input, message } = render();

      title.setValue('far too long');
      title.markAsTouched();
      fixture.detectChanges();
      fixture.detectChanges();

      expect(message()).toBe('At most 5 characters');
      expect(input.getAttribute('aria-invalid')).toBe('true');
    });
  });

  describe('when the value becomes valid again', () => {
    it('should remove the message', () => {
      const { fixture, title, message } = render();
      title.markAsTouched();
      fixture.detectChanges();

      title.setValue('ok');
      fixture.detectChanges();

      expect(message()).toBeNull();
    });
  });
});
