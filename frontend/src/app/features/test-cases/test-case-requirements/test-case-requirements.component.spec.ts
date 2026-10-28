import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestCaseRequirementsComponent } from './test-case-requirements.component';
import { RequirementApiService } from '../../../core/services/requirement-api.service';
import { Requirement } from '../../../shared/models/requirement.model';

function requirement(id: string, externalId: string): Requirement {
  return { id, externalId, title: 'T ' + externalId, description: null, testCases: [], createdAt: '', updatedAt: '' };
}

/** TM08: the requirements a case proves, linked and unlinked from the case. */
describe('TestCaseRequirementsComponent', () => {
  const linkedReq = requirement('r1', 'REQ-1');
  const otherReq = requirement('r2', 'REQ-2');
  let api: Record<string, ReturnType<typeof vi.fn>>;

  function render(canEdit: boolean) {
    TestBed.configureTestingModule({
      imports: [TestCaseRequirementsComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: RequirementApiService, useValue: api }],
    });
    const fixture = TestBed.createComponent(TestCaseRequirementsComponent);
    fixture.componentRef.setInput('projectId', 'p1');
    fixture.componentRef.setInput('testCaseId', 'c1');
    fixture.componentRef.setInput('canEdit', canEdit);
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    TestBed.resetTestingModule();
    api = {
      getForTestCase: vi.fn(() => of([linkedReq])),
      getAll: vi.fn(() => of({ content: [linkedReq, otherReq] })),
      linkTestCase: vi.fn(() => of(linkedReq)),
      unlinkTestCase: vi.fn(() => of(undefined)),
    };
  });

  it('lists the linked requirements', () => {
    const el: HTMLElement = render(false).nativeElement;

    expect(el.querySelector('[data-test-id="test-case-requirement-REQ-1"]')?.textContent).toContain('REQ-1');
  });

  it('offers no link or unlink to a reader', () => {
    const el: HTMLElement = render(false).nativeElement;

    expect(el.querySelector('[data-test-id="test-case-requirement-link-btn"]')).toBeNull();
    expect(el.querySelector('[data-test-id="test-case-requirement-unlink-REQ-1"]')).toBeNull();
  });

  it('offers only requirements that are not linked yet', () => {
    const component = render(true).componentInstance;

    component.openPicker();

    expect(component.available().map((r) => r.id)).toEqual(['r2']);
  });

  it('links the chosen requirement to this case', () => {
    const component = render(true).componentInstance;
    component.openPicker();
    component.selection = 'r2';

    component.link();

    expect(api['linkTestCase']).toHaveBeenCalledWith('p1', 'r2', 'c1');
  });

  it('unlinks a requirement from this case', () => {
    const component = render(true).componentInstance;

    component.unlink(linkedReq);

    expect(api['unlinkTestCase']).toHaveBeenCalledWith('p1', 'r1', 'c1');
  });
});
