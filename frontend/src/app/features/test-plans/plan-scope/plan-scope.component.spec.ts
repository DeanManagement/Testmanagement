import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideMockStore } from '@ngrx/store/testing';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { PlanScopeComponent } from './plan-scope.component';
import { TestPlanApiService } from '../../../core/services/test-plan-api.service';
import { TestRunApiService } from '../../../core/services/test-run-api.service';
import { TestCaseApiService } from '../../../core/services/test-case-api.service';
import { TestPlanScope } from '../../../shared/models/test-plan.model';

const SCOPE: TestPlanScope = {
  summary: { total: 2, executed: 1, notExecuted: 1, passed: 1, failed: 0, blocked: 0, skipped: 0 },
  cases: [
    { testCaseId: 'c1', key: 'P-1', title: 'Pay', priority: 'HIGH', latestStatus: 'PASSED' },
    { testCaseId: 'c2', key: 'P-2', title: 'Browse', priority: 'LOW', latestStatus: null },
  ],
};

describe('PlanScopeComponent', () => {
  const createRun = vi.fn();

  function render(): HTMLElement {
    TestBed.configureTestingModule({
      imports: [PlanScopeComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        provideMockStore({}),
        { provide: TestPlanApiService, useValue: { getScope: () => of(SCOPE) } },
        { provide: TestRunApiService, useValue: { create: createRun } },
        { provide: TestCaseApiService, useValue: {} },
      ],
    });
    const fixture = TestBed.createComponent(PlanScopeComponent);
    fixture.componentRef.setInput('projectId', 'p1');
    fixture.componentRef.setInput('planId', 'pl1');
    fixture.componentRef.setInput('planName', 'Release 3');
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  beforeEach(() => {
    TestBed.resetTestingModule();
    createRun.mockReset();
  });

  it('highlights a planned case that no run has executed yet', () => {
    const el = render();

    expect(el.querySelector('[data-test-id="scope-case-P-2"]')?.classList).toContain('not-executed');
    expect(el.querySelector('[data-test-id="scope-case-P-1"]')?.classList).not.toContain('not-executed');
  });

  it('creates a run in the plan from the unexecuted cases only', () => {
    createRun.mockReturnValue(of({ id: 'r9' }));
    const el = render();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    (el.querySelector('[data-test-id="test-plan-scope-create-run"]') as HTMLButtonElement).click();

    expect(createRun).toHaveBeenCalledWith('p1', expect.objectContaining({ testCaseIds: ['c2'], testPlanId: 'pl1' }));
    expect(navigate).toHaveBeenCalledWith(['/projects', 'p1', 'test-runs', 'r9']);
  });
});
