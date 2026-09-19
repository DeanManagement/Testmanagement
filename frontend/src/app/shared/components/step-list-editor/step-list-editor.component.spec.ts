import { TestBed } from '@angular/core/testing';
import { FormArray, FormBuilder } from '@angular/forms';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule } from '@ngx-translate/core';
import { beforeEach, describe, expect, it } from 'vitest';
import { TestStep } from '../../models/test-case.model';
import { localStepGroup, referenceStepGroup, StepImageState, StepListEditorComponent } from './step-list-editor.component';

const blockStep = (action: string): TestStep =>
  ({ id: action, action, expectedResult: '', testData: '', orderIndex: 0, imageId: null });

describe('StepListEditorComponent', () => {
  let fb: FormBuilder;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [StepListEditorComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), provideNoopAnimations()],
    });
    fb = TestBed.inject(FormBuilder);
  });

  function render(steps: FormArray, images = new Map<number, StepImageState>()) {
    const fixture = TestBed.createComponent(StepListEditorComponent);
    fixture.componentRef.setInput('steps', steps);
    fixture.componentRef.setInput('images', images);
    fixture.componentRef.setInput('projectId', 'proj');
    fixture.detectChanges();
    return fixture;
  }

  it('should show a reference as one collapsed row that expands read-only', () => {
    const steps = fb.array([localStepGroup(fb, { action: 'Reset' }),
      referenceStepGroup(fb, 'login', 'Log in', [blockStep('Open page'), blockStep('Sign in')])]);
    const fixture = render(steps);
    const el = fixture.nativeElement as HTMLElement;

    expect(el.querySelector('[data-test-id="test-case-step-shared-1"]')?.textContent).toContain('Log in');
    expect(el.querySelector('.reference-steps')).toBeNull();

    (el.querySelector('[data-test-id="test-case-step-shared-toggle-1"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect([...el.querySelectorAll('.reference-steps li')].map((li) => li.textContent?.trim()))
      .toEqual(['Open page', 'Sign in']);
    expect(el.querySelector('[data-test-id="test-case-step-action-1"]')).toBeNull();
  });

  it('should move the images of later steps up when a step is removed', () => {
    const steps = fb.array([localStepGroup(fb, { action: 'A' }), localStepGroup(fb, { action: 'B' }),
      localStepGroup(fb, { action: 'C' })]);
    const images = new Map<number, StepImageState>([[0, { id: 'img-a' }], [2, { id: 'img-c' }]]);
    const fixture = render(steps, images);

    fixture.componentInstance.removeStep(1);

    expect([...images.entries()]).toEqual([[0, { id: 'img-a' }], [1, { id: 'img-c' }]]);
    expect(steps.length).toBe(2);
  });
});
