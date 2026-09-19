import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ChangeBugStatusDialogComponent, ChangeBugStatusDialogData } from './change-bug-status-dialog.component';
import { BugReportApiService } from '../../../core/services/bug-report-api.service';
import { BugReport, BugReportStatus } from '../../../shared/models/bug-report.model';

/** PRD-045 §3.1: the dialog only lets through a change the server would accept. */
describe('ChangeBugStatusDialogComponent', () => {
  let close: ReturnType<typeof vi.fn>;

  function create(newStatus: BugReportStatus): ChangeBugStatusDialogComponent {
    TestBed.resetTestingModule();
    close = vi.fn();
    TestBed.configureTestingModule({
      imports: [ChangeBugStatusDialogComponent, TranslateModule.forRoot()],
      providers: [
        provideNoopAnimations(),
        { provide: MatDialogRef, useValue: { close } },
        { provide: MAT_DIALOG_DATA, useValue: { projectId: 'p1', newStatus, bugIds: ['b1'] } as ChangeBugStatusDialogData },
        { provide: BugReportApiService, useValue: { getAll: () => of({ content: [] }) } },
      ],
    });
    const fixture = TestBed.createComponent(ChangeBugStatusDialogComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('needs a resolution to close a bug', () => {
    const dialog = create('CLOSED');
    dialog.reason = 'done';

    expect(dialog.canConfirm).toBe(false);
    dialog.resolution = 'FIXED';
    expect(dialog.canConfirm).toBe(true);
  });

  it('needs the original bug for a duplicate', () => {
    const dialog = create('CLOSED');
    dialog.reason = 'same crash';
    dialog.resolution = 'DUPLICATE';

    expect(dialog.canConfirm).toBe(false);
    dialog.duplicateOf = { id: 'b0' } as BugReport;
    expect(dialog.canConfirm).toBe(true);
  });

  it('offers no resolution when reopening', () => {
    const dialog = create('OPEN');
    dialog.reason = 'still happens';

    dialog.onConfirm();

    expect(dialog.showsResolution).toBe(false);
    expect(close).toHaveBeenCalledWith({ status: 'OPEN', reason: 'still happens', resolution: null, duplicateOfId: null });
  });

  it('sends the duplicate target with the request', () => {
    const dialog = create('CLOSED');
    dialog.reason = 'same crash';
    dialog.resolution = 'DUPLICATE';
    dialog.duplicateOf = { id: 'b0' } as BugReport;

    dialog.onConfirm();

    expect(close).toHaveBeenCalledWith(expect.objectContaining({ resolution: 'DUPLICATE', duplicateOfId: 'b0' }));
  });
});
