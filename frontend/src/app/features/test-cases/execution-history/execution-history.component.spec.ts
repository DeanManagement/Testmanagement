import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';
import { ExecutionHistoryComponent } from './execution-history.component';
import { TestCaseApiService } from '../../../core/services/test-case-api.service';
import { TestCaseExecution } from '../../../shared/models/test-case.model';

describe('ExecutionHistoryComponent', () => {
  const row = (executedVersion: number | null): TestCaseExecution => ({
    resultId: 'r1', runId: 'run1', runKey: 'P-Run-7', runName: 'Nightly', runStatus: 'COMPLETED', environment: 'staging',
    parameterSetName: null, status: 'FAILED', executedAt: '2026-09-19T10:00:00Z', executorName: 'Tess',
    executedVersion, durationMs: null,
  });

  function render(rows: TestCaseExecution[], currentVersion = 3): HTMLElement {
    TestBed.configureTestingModule({
      imports: [ExecutionHistoryComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        { provide: TestCaseApiService, useValue: {
          getExecutions: () => of({ content: rows, page: { number: 0, size: 20, totalElements: rows.length, totalPages: 1 } }),
        } },
      ],
    });
    const fixture = TestBed.createComponent(ExecutionHistoryComponent);
    fixture.componentRef.setInput('projectId', 'p1');
    fixture.componentRef.setInput('testCaseId', 'c1');
    fixture.componentRef.setInput('currentVersion', currentVersion);
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  beforeEach(() => TestBed.resetTestingModule());

  it('links each row to its result in the run and names the executor', () => {
    const el = render([row(3)]);

    expect(el.querySelector('a')?.getAttribute('href')).toBe('/projects/p1/test-runs/run1?result=r1');
    expect(el.textContent).toContain('Tess');
    expect(el.querySelector('[data-test-id="executions-older-wording"]')).toBeNull();
  });

  it('marks a result that ran an older version of the case', () => {
    expect(render([row(2)]).querySelector('[data-test-id="executions-older-wording"]')).not.toBeNull();
  });

  it('says when the case has not run yet', () => {
    expect(render([]).querySelector('[data-test-id="executions-empty"]')).not.toBeNull();
  });
});
