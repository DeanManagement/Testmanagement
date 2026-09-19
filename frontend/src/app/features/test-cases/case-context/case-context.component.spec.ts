import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { Observable, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';
import { CaseContextComponent } from './case-context.component';
import { TestCaseApiService } from '../../../core/services/test-case-api.service';
import { TestCase, TestCaseContext } from '../../../shared/models/test-case.model';

/** PRD-050: where a case sits, who made it, and which suites include it. */
describe('CaseContextComponent', () => {
  function render(response: Observable<TestCaseContext>): HTMLElement {
    TestBed.configureTestingModule({
      imports: [CaseContextComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: TestCaseApiService, useValue: { getContext: () => response } }],
    });
    const fixture = TestBed.createComponent(CaseContextComponent);
    fixture.componentRef.setInput('projectId', 'p1');
    fixture.componentRef.setInput('testCase', { id: 'c1', createdAt: '2026-09-01T10:00:00Z', updatedAt: '2026-09-02T10:00:00Z' } as TestCase);
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  beforeEach(() => TestBed.resetTestingModule());

  it('shows the folder path root first, linked, and the suites', () => {
    const el = render(of({
      createdByName: 'Tess', updatedByName: 'Lead',
      folderPath: [{ id: 'f1', name: 'Checkout' }, { id: 'f2', name: 'Payment' }],
      suites: [{ id: 's1', name: 'Smoke' }],
    }));

    const links = el.querySelectorAll('[data-test-id="case-folder-path"] a');
    expect([...links].map((a) => a.textContent?.trim())).toEqual(['Checkout', 'Payment']);
    expect(links[1].getAttribute('href')).toBe('/projects/p1/test-cases?folderId=f2');
    expect(el.querySelector('[data-test-id="case-suites"] a')?.getAttribute('href')).toBe('/projects/p1/test-suites/s1');
  });

  it('says so when the case is in no folder and no suite', () => {
    const el = render(of({ createdByName: null, updatedByName: null, folderPath: [], suites: [] }));

    expect(el.querySelector('[data-test-id="case-folder-path"]')?.textContent).toContain('testCase.context.noFolder');
    expect(el.querySelector('[data-test-id="case-suites"]')?.textContent).toContain('testCase.context.noSuites');
  });

  it('shows a short note, not an error page, when the context fails to load', () => {
    const el = render(throwError(() => new Error('boom')));

    expect(el.textContent).toContain('testCase.context.unavailable');
  });
});
