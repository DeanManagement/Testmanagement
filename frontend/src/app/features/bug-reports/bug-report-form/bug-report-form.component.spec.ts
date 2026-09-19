import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideMockStore } from '@ngrx/store/testing';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';
import { BugReportFormComponent } from './bug-report-form.component';
import { ProjectApiService } from '../../../core/services/project-api.service';
import { ProjectMemberApiService } from '../../../core/services/project-member-api.service';
import { EnvironmentApiService } from '../../../core/services/environment-api.service';
import { CustomFieldApiService } from '../../../core/services/custom-field-api.service';
import { initialBugReportState } from '../../../store/bug-report/bug-report.state';

/** PRD-045: the project's template fills a new report's empty fields, never an edited one. */
describe('BugReportFormComponent – template', () => {
  const project = { bugTemplateDescription: 'What happened?', bugTemplateSteps: '1. ', bugTemplateEnvironment: 'staging' };

  function create(params: Record<string, string>, query: Record<string, string> = {}): BugReportFormComponent {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [BugReportFormComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        provideMockStore({ initialState: { bugReports: initialBugReportState } }),
        { provide: ProjectApiService, useValue: { getById: () => of(project) } },
        { provide: ProjectMemberApiService, useValue: { getByProject: () => of([]) } },
        { provide: EnvironmentApiService, useValue: { getAll: () => of([]), getActive: () => of([]), invalidate: () => undefined } },
        { provide: CustomFieldApiService, useValue: { getActive: () => of([]) } },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { paramMap: convertToParamMap(params), queryParams: query },
            parent: { snapshot: { paramMap: convertToParamMap({ id: 'p1' }) } },
          },
        },
      ],
    });
    const fixture = TestBed.createComponent(BugReportFormComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  beforeEach(() => TestBed.resetTestingModule());

  it('pre-fills a new report from the template', () => {
    const form = create({});

    expect(form.form.value.description).toBe('What happened?');
    expect(form.form.value.stepsToReproduce).toBe('1. ');
    expect(form.form.value.environment).toBe('staging');
    expect(form.hasUnsavedChanges()).toBe(false);
  });

  it('keeps what a test run already filled in', () => {
    const form = create({}, { description: 'Checkout returned 500', environment: 'prod' });

    expect(form.form.value.description).toBe('Checkout returned 500');
    expect(form.form.value.environment).toBe('prod');
  });

  it('never applies the template when editing', () => {
    const form = create({ bugId: 'b1' });

    expect(form.form.value.description).toBe('');
  });
});
