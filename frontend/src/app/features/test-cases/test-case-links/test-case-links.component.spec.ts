import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestCaseLinksComponent } from './test-case-links.component';
import { TestCaseApiService } from '../../../core/services/test-case-api.service';
import { TestCaseLink } from '../../../shared/models/test-case-link.model';
import { TestCase } from '../../../shared/models/test-case.model';

/** TM07: links to other cases, added through a case search and removed per link. */
describe('TestCaseLinksComponent', () => {
  const setup: TestCaseLink = {
    id: 'l1', linkType: 'PRECONDITION', direction: 'OUTGOING', testCaseId: 'c0', testCaseKey: 'SN-1', testCaseTitle: 'Log in',
  };
  let api: Record<string, ReturnType<typeof vi.fn>>;

  function render(canEdit: boolean) {
    TestBed.configureTestingModule({
      imports: [TestCaseLinksComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: TestCaseApiService, useValue: api }],
    });
    const fixture = TestBed.createComponent(TestCaseLinksComponent);
    fixture.componentRef.setInput('projectId', 'p1');
    fixture.componentRef.setInput('testCaseId', 'c1');
    fixture.componentRef.setInput('canEdit', canEdit);
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    TestBed.resetTestingModule();
    api = {
      getLinks: vi.fn(() => of([setup])),
      getAll: vi.fn(() => of({ content: [{ id: 'c1', key: 'SN-5' }, { id: 'c2', key: 'SN-6' }] })),
      link: vi.fn(() => of([setup])),
      unlink: vi.fn(() => of(undefined)),
    };
  });

  it('lists the links and emits them for the run-first line', () => {
    const fixture = render(false);
    const emitted: TestCaseLink[][] = [];
    fixture.componentInstance.linksChange.subscribe((links) => emitted.push(links));

    fixture.componentInstance.unlink(setup);

    expect(fixture.nativeElement.querySelector('[data-test-id="test-case-link-SN-1"]')?.textContent).toContain('SN-1');
    expect(emitted).toEqual([[setup]]);
  });

  it('offers no add or remove to a reader', () => {
    const el: HTMLElement = render(false).nativeElement;

    expect(el.querySelector('[data-test-id="test-case-link-add-btn"]')).toBeNull();
    expect(el.querySelector('[data-test-id="test-case-link-remove-SN-1"]')).toBeNull();
  });

  it('never offers the case itself as a link target', () => {
    const component = render(true).componentInstance;

    component.search('SN');

    expect(component.candidates().map((tc) => tc.id)).toEqual(['c2']);
  });

  it('links the chosen case with the chosen type', () => {
    const component = render(true).componentInstance;
    component.openPicker();
    component.linkType = 'RELATES_TO';
    component.selection = { id: 'c2' } as TestCase;

    component.link();

    expect(api['link']).toHaveBeenCalledWith('p1', 'c1', 'c2', 'RELATES_TO');
  });

  it('removes a link from this case', () => {
    const component = render(true).componentInstance;

    component.unlink(setup);

    expect(api['unlink']).toHaveBeenCalledWith('p1', 'c1', 'l1');
  });
});
