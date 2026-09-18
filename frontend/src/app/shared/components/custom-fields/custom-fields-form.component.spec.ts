import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';
import { CustomFieldApiService } from '../../../core/services/custom-field-api.service';
import { CustomField, CustomFieldType, CustomFieldValues } from '../../models/custom-field.model';
import { CustomFieldsFormComponent } from './custom-fields-form.component';

function field(name: string, fieldType: CustomFieldType, over: Partial<CustomField> = {}): CustomField {
  return { id: `id-${name}`, entityType: 'TEST_CASE', name, fieldType, options: [], required: false, archived: false, orderIndex: 0, ...over };
}

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, CustomFieldsFormComponent],
  template: '<app-custom-fields-form projectId="p1" entityType="TEST_CASE" [formControl]="control" />',
})
class HostComponent {
  readonly control = new FormControl<CustomFieldValues>({});
}

/** The API fake: whatever active fields the test defines. */
function render(fields: CustomField[], initial?: CustomFieldValues): ComponentFixture<HostComponent> {
  TestBed.configureTestingModule({
    imports: [HostComponent, TranslateModule.forRoot()],
    providers: [{ provide: CustomFieldApiService, useValue: { getActive: () => of(fields) } }],
  });
  const fixture = TestBed.createComponent(HostComponent);
  if (initial) {
    fixture.componentInstance.control.setValue(initial);
  }
  fixture.detectChanges();
  return fixture;
}

function input(fixture: ComponentFixture<HostComponent>, name: string): HTMLInputElement {
  return (fixture.nativeElement as HTMLElement).querySelector(`[data-test-id="custom-field-${name}"]`) as HTMLInputElement;
}

function type(fixture: ComponentFixture<HostComponent>, name: string, value: string): void {
  const element = input(fixture, name);
  element.value = value;
  element.dispatchEvent(new Event('input'));
  fixture.detectChanges();
}

describe('CustomFieldsFormComponent', () => {
  beforeEach(() => TestBed.resetTestingModule());

  describe('when the project has fields of each type', () => {
    const fields = [
      field('Customer', 'TEXT'),
      field('Effort', 'NUMBER'),
      field('Due', 'DATE'),
      field('Component', 'SELECT', { options: ['Checkout', 'Search'] }),
      field('Browsers', 'MULTI_SELECT', { options: ['Chrome', 'Firefox'] }),
    ];

    it('should render a text, number and date input and two selects', () => {
      const fixture = render(fields);

      expect(input(fixture, 'Customer').type).toBe('text');
      expect(input(fixture, 'Effort').type).toBe('number');
      expect(input(fixture, 'Due').type).toBe('date');
      expect(input(fixture, 'Component').tagName).toBe('MAT-SELECT');
      expect(input(fixture, 'Component').classList.contains('mat-mdc-select-multiple')).toBe(false);
      expect(input(fixture, 'Browsers').classList.contains('mat-mdc-select-multiple')).toBe(true);
    });

    it('should show the values it was given', () => {
      const fixture = render(fields, { Customer: 'ACME', Due: '2026-10-01', Effort: 2.5 });

      expect(input(fixture, 'Customer').value).toBe('ACME');
      expect(input(fixture, 'Due').value).toBe('2026-10-01');
      expect(input(fixture, 'Effort').value).toBe('2.5');
    });

    it('should emit a map keyed by field name, with null for every empty field', () => {
      const fixture = render(fields);

      type(fixture, 'Customer', ' ACME ');

      expect(fixture.componentInstance.control.value)
        .toEqual({ Customer: 'ACME', Effort: null, Due: null, Component: null, Browsers: null });
    });
  });

  describe('when a field is required', () => {
    it('should make the parent control invalid until it is filled', () => {
      const fixture = render([field('Owner', 'TEXT', { required: true })]);
      expect(fixture.componentInstance.control.invalid).toBe(true);

      type(fixture, 'Owner', 'Dana');

      expect(fixture.componentInstance.control.valid).toBe(true);
    });
  });

  describe('when the project has no fields', () => {
    it('should render nothing and stay valid', () => {
      const fixture = render([]);

      expect((fixture.nativeElement as HTMLElement).querySelector('[data-test-id="custom-fields-form"]')).toBeNull();
      expect(fixture.componentInstance.control.valid).toBe(true);
    });
  });
});
