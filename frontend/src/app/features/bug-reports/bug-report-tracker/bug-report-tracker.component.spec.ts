import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { BugReportApiService } from '../../../core/services/bug-report-api.service';
import { IssueTrackerApiService } from '../../../core/services/issue-tracker-api.service';
import { IssueLink } from '../../../shared/models/issue-tracker.model';
import { BugReportTrackerComponent } from './bug-report-tracker.component';

const LINK = { id: 'l1', testResultId: null, bugReportId: 'bug-1', provider: 'AZURE_DEVOPS', externalId: '41',
  url: 'https://dev.azure.com/c/P/_workitems/edit/41', title: 'Checkout fails', state: 'OPEN', trackerState: 'Active',
  stateCheckedAt: null } as IssueLink;

describe('BugReportTrackerComponent', () => {
  let api: Record<string, ReturnType<typeof vi.fn>>;

  beforeEach(() => {
    api = {
      getTrackerIssue: vi.fn(() => of(null)),
      handOver: vi.fn(() => of(LINK)),
      detachTrackerIssue: vi.fn(() => of(undefined)),
    };
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [BugReportTrackerComponent, TranslateModule.forRoot()],
      providers: [
        provideNoopAnimations(),
        { provide: BugReportApiService, useValue: api },
        { provide: IssueTrackerApiService, useValue: { getStatus: () => of({ configured: true, provider: 'AZURE_DEVOPS' }) } },
      ],
    });
  });

  function render() {
    const fixture = TestBed.createComponent(BugReportTrackerComponent);
    fixture.componentRef.setInput('projectId', 'p');
    fixture.componentRef.setInput('bugId', 'bug-1');
    fixture.componentRef.setInput('canEdit', true);
    const linked: boolean[] = [];
    fixture.componentInstance.linkedChange.subscribe((value) => linked.push(value));
    fixture.detectChanges();
    return { component: fixture.componentInstance, linked };
  }

  it('should file a new work item when no existing id is given', () => {
    const { component } = render();

    component.handOver();

    expect(api['handOver']).toHaveBeenCalledWith('p', 'bug-1', undefined);
  });

  it('should link the existing work item that was typed in', () => {
    const { component } = render();
    component.existingId = ' 41 ';

    component.handOver();

    expect(api['handOver']).toHaveBeenCalledWith('p', 'bug-1', '41');
  });

  it('should tell the page the status is owned by the tracker after a handover, and not after detaching', () => {
    const { component, linked } = render();

    component.handOver();
    component.detach();

    expect(linked).toEqual([false, true, false]);
  });
});
