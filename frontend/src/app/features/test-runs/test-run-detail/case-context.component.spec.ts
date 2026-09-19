import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { beforeEach, describe, expect, it } from 'vitest';
import { CaseContextComponent } from './case-context.component';
import { TestResult } from '../../../shared/models/test-run.model';

/** PRD-048: the tester sees the case's preconditions, and is told when it changed since execution. */
describe('CaseContextComponent', () => {
  function render(overrides: Partial<TestResult>): HTMLElement {
    const fixture = TestBed.createComponent(CaseContextComponent);
    fixture.componentRef.setInput('result', {
      testCaseId: 'c1', testCasePreconditions: null, testCaseDescription: null, executedVersion: 2,
      testCaseVersion: 2, ...overrides,
    } as TestResult);
    fixture.componentRef.setInput('projectId', 'p1');
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ imports: [CaseContextComponent, TranslateModule.forRoot()], providers: [provideRouter([])] });
  });

  it('opens when the case has preconditions', () => {
    const details = render({ testCasePreconditions: 'Logged in' })
      .querySelector('[data-test-id="execution-case-context"]') as HTMLDetailsElement;

    expect(details.open).toBe(true);
    expect(details.textContent).toContain('Logged in');
  });

  it('stays closed with only a description, and is absent with neither', () => {
    const details = render({ testCaseDescription: 'Pays by card' })
      .querySelector('[data-test-id="execution-case-context"]') as HTMLDetailsElement;

    expect(details.open).toBe(false);
    expect(render({}).querySelector('[data-test-id="execution-case-context"]')).toBeNull();
  });

  it('says when the case changed since the result was recorded', () => {
    expect(render({ executedVersion: 2, testCaseVersion: 3 }).querySelector('[data-test-id="execution-case-changed"]'))
      .not.toBeNull();
    expect(render({ executedVersion: 3, testCaseVersion: 3 }).querySelector('[data-test-id="execution-case-changed"]'))
      .toBeNull();
  });
});
