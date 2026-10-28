import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CaseContextComponent } from './case-context.component';
import { TestResult } from '../../../shared/models/test-run.model';
import { TestCaseApiService } from '../../../core/services/test-case-api.service';
import { TestCaseLink } from '../../../shared/models/test-case-link.model';

/** PRD-048: the tester sees the case's preconditions, and is told when it changed since execution. */
describe('CaseContextComponent', () => {
  let links: TestCaseLink[] = [];

  function render(overrides: Partial<TestResult>, runResults: Partial<TestResult>[] = []): HTMLElement {
    const fixture = TestBed.createComponent(CaseContextComponent);
    fixture.componentRef.setInput('result', {
      testCaseId: 'c1', testCasePreconditions: null, testCaseDescription: null, executedVersion: 2,
      testCaseVersion: 2, ...overrides,
    } as TestResult);
    fixture.componentRef.setInput('projectId', 'p1');
    fixture.componentRef.setInput('runResults', runResults as TestResult[]);
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  beforeEach(() => {
    TestBed.resetTestingModule();
    links = [];
    TestBed.configureTestingModule({
      imports: [CaseContextComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: TestCaseApiService, useValue: { getLinks: vi.fn(() => of(links)) } }],
    });
  });

  function precondition(key: string, testCaseId: string): TestCaseLink {
    return { id: 'l-' + key, linkType: 'PRECONDITION', direction: 'OUTGOING', testCaseId, testCaseKey: key, testCaseTitle: 'Setup' };
  }

  it('lists precondition cases with their result in this run (TM07)', () => {
    links = [precondition('SN-1', 'c0'), precondition('SN-2', 'c9'),
      { ...precondition('SN-3', 'c3'), direction: 'INCOMING' }];

    const el = render({}, [{ testCaseId: 'c0', status: 'PASSED', executedAt: '2026-10-28T10:00:00Z' }]);

    expect(el.querySelector('[data-test-id="execution-run-first-SN-1"]')?.textContent).toContain('resultStatus.PASSED');
    expect(el.querySelector('[data-test-id="execution-run-first-SN-2"]')?.textContent).toContain('testCase.links.notInRun');
    expect(el.querySelector('[data-test-id="execution-run-first-SN-3"]')).toBeNull();
  });

  it('shows no precondition block when the case has none', () => {
    expect(render({}).querySelector('[data-test-id="execution-run-first"]')).toBeNull();
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

  it('renders preconditions and description as Markdown (TM03)', () => {
    const el = render({ testCasePreconditions: '- **Admin** logged in', testCaseDescription: '**Testziel:** pay' });

    expect(el.querySelector('[data-test-id="execution-case-preconditions"] li strong')?.textContent).toBe('Admin');
    expect(el.querySelector('[data-test-id="execution-case-description"] strong')?.textContent).toBe('Testziel:');
  });

  it('says when the case changed since the result was recorded', () => {
    expect(render({ executedVersion: 2, testCaseVersion: 3 }).querySelector('[data-test-id="execution-case-changed"]'))
      .not.toBeNull();
    expect(render({ executedVersion: 3, testCaseVersion: 3 }).querySelector('[data-test-id="execution-case-changed"]'))
      .toBeNull();
  });
});
