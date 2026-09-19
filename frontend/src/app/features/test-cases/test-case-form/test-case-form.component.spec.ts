import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideMockStore } from '@ngrx/store/testing';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CustomFieldApiService } from '../../../core/services/custom-field-api.service';
import { ProjectApiService } from '../../../core/services/project-api.service';
import { TestCaseApiService } from '../../../core/services/test-case-api.service';
import { GherkinPreview } from '../../../shared/models/test-case.model';
import { SharedStep } from '../../../shared/models/shared-step.model';
import { MatDialog } from '@angular/material/dialog';
import { TestCaseFormComponent } from './test-case-form.component';

const PREVIEW: GherkinPreview = {
  key: null, title: 'From Gherkin', description: 'Read back', preconditions: null, priority: null,
  labels: ['smoke'], steps: [{ action: 'Given {amount}', expectedResult: '', testData: '| a |' }],
  parameterSets: [], problems: [], warnings: [],
};

describe('TestCaseFormComponent – Edit as Gherkin (PRD-040 §3.7)', () => {
  let previewGherkin: ReturnType<typeof vi.fn>;
  let create: ReturnType<typeof vi.fn>;
  let pickedSharedStep: SharedStep | undefined;

  beforeEach(() => {
    TestBed.resetTestingModule();
    previewGherkin = vi.fn(() => of(PREVIEW));
    create = vi.fn(() => of({ id: 'new', steps: [] }));
    pickedSharedStep = undefined;
    TestBed.configureTestingModule({
      imports: [TestCaseFormComponent, TranslateModule.forRoot()],
      providers: [
        // Saving navigates to the saved case; any route will do here.
        provideRouter([{ path: '**', children: [] }]),
        provideNoopAnimations(),
        provideMockStore(),
        { provide: TestCaseApiService, useValue: { previewGherkin, create } },
        { provide: MatDialog, useValue: { open: () => ({ afterClosed: () => of(pickedSharedStep) }) } },
        { provide: ProjectApiService, useValue: { getById: () => of({ reviewRequired: false }) } },
        { provide: CustomFieldApiService, useValue: { getActive: () => of([]) } },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { paramMap: convertToParamMap({}), queryParamMap: convertToParamMap({}) },
            parent: { snapshot: { paramMap: convertToParamMap({ id: 'proj' }) } },
          },
        },
      ],
    });
  });

  function createForm(): TestCaseFormComponent {
    const fixture = TestBed.createComponent(TestCaseFormComponent);
    fixture.detectChanges();
    const form = fixture.componentInstance;
    form.form.patchValue({ title: 'Login', labels: 'ui' });
    form.addStep();
    form.steps.at(0).patchValue({ action: 'Open the page', expectedResult: 'It loads' });
    return form;
  }

  it('should open with the current case written as a scenario', () => {
    const form = createForm();

    form.openGherkin();

    expect(form.gherkinText).toBe('@ui\nScenario: Login\n  * Open the page\n');
    expect(form.gherkinDropsExpectedResults).toBe(true);
  });

  it('should replace title, description, labels and steps with what the server read', () => {
    const form = createForm();
    form.openGherkin();
    form.gherkinText = 'Scenario: From Gherkin\n  Given <amount>\n';

    form.applyGherkin();

    expect(previewGherkin).toHaveBeenCalledWith('proj', 'Scenario: From Gherkin\n  Given <amount>\n');
    expect(form.form.value.title).toBe('From Gherkin');
    expect(form.form.value.labels).toBe('smoke');
    expect(form.steps.value).toMatchObject([{ action: 'Given {amount}', expectedResult: '', testData: '| a |', sharedStepId: null }]);
    expect(form.gherkinText).toBeNull();
    expect(form.hasUnsavedChanges()).toBe(true);
  });

  it('should keep the editor open and the steps untouched when the text does not parse', () => {
    previewGherkin.mockReturnValue(throwError(() => new HttpErrorResponse({
      status: 400, error: { message: 'scenario: (3:1): unexpected end of file' },
    })));
    const form = createForm();
    form.openGherkin();

    form.applyGherkin();

    expect(form.gherkinError).toBe('scenario: (3:1): unexpected end of file');
    expect(form.gherkinText).not.toBeNull();
    expect(form.steps.value[0].action).toBe('Open the page');
  });

  it('should refuse a scenario an import would refuse', () => {
    previewGherkin.mockReturnValue(of({ ...PREVIEW, problems: ['the scenario has no name'] }));
    const form = createForm();
    form.openGherkin();

    form.applyGherkin();

    expect(form.gherkinError).toBe('the scenario has no name');
    expect(form.form.value.title).toBe('Login');
  });

  it('should not wipe the steps when the text yields none', () => {
    previewGherkin.mockReturnValue(of({ ...PREVIEW, steps: [], description: 'Wenn er sich anmeldet' }));
    const form = createForm();
    form.openGherkin();

    form.applyGherkin();

    expect(form.gherkinError).toBe('testCase.form.gherkin.noSteps');
    expect(form.steps.length).toBe(1);
  });

  describe('shared steps (PRD-030)', () => {
    const LOGIN: SharedStep = {
      id: 'login', title: 'Log in', description: null, usedByCount: 0, createdAt: '', updatedAt: '',
      steps: [{ id: 's1', action: 'Open page', expectedResult: '', testData: '', orderIndex: 0, imageId: null }],
    };

    it('should insert a reference and save it as a sharedStepId only', () => {
      pickedSharedStep = LOGIN;
      const form = createForm();

      form.insertSharedStep();
      form.onSubmit();

      expect(form.hasReferences).toBe(true);
      expect(create.mock.calls[0][1].steps).toEqual([
        { action: 'Open the page', expectedResult: 'It loads', testData: undefined },
        { sharedStepId: 'login' },
      ]);
    });

    it('should leave the steps alone when the picker is closed without a choice', () => {
      const form = createForm();

      form.insertSharedStep();

      expect(form.steps.length).toBe(1);
    });
  });

  describe('unsaved changes', () => {
    function freshForm(): TestCaseFormComponent {
      const fixture = TestBed.createComponent(TestCaseFormComponent);
      fixture.detectChanges();
      return fixture.componentInstance;
    }

    it('should not count a form nobody has touched', () => {
      expect(freshForm().hasUnsavedChanges()).toBe(false);
    });

    it('should count an edit to any field, not just the title', () => {
      const form = freshForm();

      form.form.controls.description.setValue('Changed');
      form.form.controls.description.markAsDirty();

      expect(form.hasUnsavedChanges()).toBe(true);
    });

    it('should count a step added or removed', () => {
      const form = freshForm();

      form.addStep();
      form.steps.markAsDirty();

      expect(form.hasUnsavedChanges()).toBe(true);
    });

    it('should let a save navigate away', () => {
      const form = createForm();
      form.form.controls.title.markAsDirty();

      form.onSubmit();

      expect(form.hasUnsavedChanges()).toBe(false);
    });
  });
});
