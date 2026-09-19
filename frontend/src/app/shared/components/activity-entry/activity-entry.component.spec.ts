import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { beforeEach, describe, expect, it } from 'vitest';
import { ActivityEntryComponent } from './activity-entry.component';
import { AuditEntry } from '../../models/activity.model';

/** PRD-046: an entry shows what changed, and links to its object while that exists. */
describe('ActivityEntryComponent', () => {
  function entry(overrides: Partial<AuditEntry> = {}): AuditEntry {
    return {
      id: 'e1', projectId: 'p1', userId: 'u1', userDisplayName: 'Ada', action: 'UPDATED', entityType: 'BUG_REPORT',
      entityId: 'b1', entityName: 'P-BUG-1 Crash', details: null, createdAt: '2026-09-19T10:00:00Z',
      changes: [], parentEntityType: null, parentEntityId: null, link: { type: 'BUG_REPORT', id: 'b1' },
      ...overrides,
    };
  }

  function render(value: AuditEntry, currentEntityId: string | null = null): HTMLElement {
    const fixture = TestBed.createComponent(ActivityEntryComponent);
    fixture.componentRef.setInput('entry', value);
    fixture.componentRef.setInput('projectId', 'p1');
    fixture.componentRef.setInput('currentEntityId', currentEntityId);
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [ActivityEntryComponent, TranslateModule.forRoot()],
      providers: [provideRouter([])],
    });
    TestBed.inject(TranslateService).setTranslation('en', {
      activity: { fields: { status: 'Status' } },
      bugReport: { status: { NEW: 'New', CLOSED: 'Closed' } },
    });
    TestBed.inject(TranslateService).use('en');
  });

  it('shows a status change as translated old and new values', () => {
    const el = render(entry({ changes: [{ field: 'status', from: 'NEW', to: 'CLOSED' }] }));

    expect(el.querySelector('[data-test-id="activity-changes"]')?.textContent?.replace(/\s+/g, ''))
      .toBe('Status:New→Closed');
  });

  it('shows an emptied field as a dash and an unknown field by its name', () => {
    const el = render(entry({ changes: [{ field: 'assignee', from: 'Ada', to: null }] }));

    expect(el.querySelector('[data-test-id="activity-changes"]')?.textContent?.replace(/\s+/g, ''))
      .toBe('assignee:Ada→—');
  });

  it('links to the object while it exists', () => {
    const el = render(entry());

    expect(el.querySelector('a')?.getAttribute('href')).toBe('/projects/p1/bug-reports/b1');
  });

  it('does not link once the object is gone', () => {
    const el = render(entry({ link: null }));

    expect(el.querySelector('a')).toBeNull();
  });

  it('does not link back to the object whose history this is', () => {
    const el = render(entry(), 'b1');

    expect(el.querySelector('a')).toBeNull();
  });
});
