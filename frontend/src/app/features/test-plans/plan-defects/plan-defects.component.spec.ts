import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';
import { PlanDefectsComponent } from './plan-defects.component';
import { BugReportApiService } from '../../../core/services/bug-report-api.service';
import { BugReport } from '../../../shared/models/bug-report.model';

describe('PlanDefectsComponent', () => {
  function render(response: ReturnType<BugReportApiService['getByTestPlan']>): HTMLElement {
    TestBed.configureTestingModule({
      imports: [PlanDefectsComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: BugReportApiService, useValue: { getByTestPlan: () => response } }],
    });
    const fixture = TestBed.createComponent(PlanDefectsComponent);
    fixture.componentRef.setInput('projectId', 'p1');
    fixture.componentRef.setInput('planId', 'pl1');
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  beforeEach(() => TestBed.resetTestingModule());

  it("lists the plan's bugs with their keys", () => {
    const bug = { id: 'b1', key: 'P-BUG-1', title: 'Crash', status: 'NEW', priority: 'HIGH', testRunId: 'r1',
      testRunName: 'Nightly' } as BugReport;

    const el = render(of([bug]));

    expect(el.querySelector('[data-test-id="plan-defect-P-BUG-1"]')?.textContent).toContain('P-BUG-1 Crash');
  });

  it('shows nothing when bug reports are off for the project', () => {
    const el = render(throwError(() => new Error('403')));

    expect(el.querySelector('[data-test-id="test-plan-defects"]')).toBeNull();
  });
});
