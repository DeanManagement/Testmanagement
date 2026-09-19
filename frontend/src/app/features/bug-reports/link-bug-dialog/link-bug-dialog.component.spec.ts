import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { LinkBugDialogComponent, openFirst } from './link-bug-dialog.component';
import { BugReportApiService } from '../../../core/services/bug-report-api.service';
import { BugReport } from '../../../shared/models/bug-report.model';

describe('LinkBugDialogComponent', () => {
  const bug = (id: string, status: BugReport['status']) => ({ id, key: id, title: id, status }) as BugReport;

  it('lists open bugs before resolved and closed ones', () => {
    const ordered = openFirst([bug('a', 'CLOSED'), bug('b', 'NEW'), bug('c', 'RESOLVED'), bug('d', 'IN_PROGRESS')]);

    expect(ordered.map((b) => b.id)).toEqual(['b', 'd', 'a', 'c']);
  });

  it('does not offer bugs already on the result, and closes with the one picked', async () => {
    const close = vi.fn();
    TestBed.configureTestingModule({
      imports: [LinkBugDialogComponent, TranslateModule.forRoot()],
      providers: [
        provideNoopAnimations(),
        { provide: MatDialogRef, useValue: { close } },
        { provide: MAT_DIALOG_DATA, useValue: { projectId: 'p1', excludeIds: ['a'] } },
        { provide: BugReportApiService, useValue: { getAll: () => of({ content: [bug('a', 'NEW'), bug('b', 'NEW')] }) } },
      ],
    });
    const fixture = TestBed.createComponent(LinkBugDialogComponent);
    fixture.detectChanges();
    const dialog = fixture.componentInstance;

    expect(dialog.bugs().map((b) => b.id)).toEqual(['b']);
    dialog.pick(dialog.bugs()[0]);
    expect(close).toHaveBeenCalledWith(dialog.bugs()[0]);
  });
});
