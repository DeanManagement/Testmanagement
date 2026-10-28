import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ImportRequirementsDialogComponent } from './import-requirements-dialog.component';
import { RequirementApiService } from '../../core/services/requirement-api.service';
import { ImportResult } from '../../shared/models/test-case.model';

/** TM19: a dry run previews the rows; the real import closes with its result. */
describe('ImportRequirementsDialogComponent', () => {
  const result = (dryRun: boolean): ImportResult => ({
    imported: 2, updated: 1, skipped: 1, dryRun, errors: [{ row: 3, message: 'unknown parentExternalId' }],
    warnings: [], unchanged: 0,
  });
  let api: { importCsv: ReturnType<typeof vi.fn> };
  let close: ReturnType<typeof vi.fn>;

  function render() {
    TestBed.configureTestingModule({
      imports: [ImportRequirementsDialogComponent, TranslateModule.forRoot()],
      providers: [
        { provide: RequirementApiService, useValue: api },
        { provide: MatDialogRef, useValue: { close } },
        { provide: MAT_DIALOG_DATA, useValue: { projectId: 'p1' } },
      ],
    });
    const fixture = TestBed.createComponent(ImportRequirementsDialogComponent);
    fixture.componentInstance.file = new File(['externalId,title'], 'r.csv');
    return fixture;
  }

  beforeEach(() => {
    TestBed.resetTestingModule();
    api = { importCsv: vi.fn((_p: string, _f: File, dryRun: boolean) => of(result(dryRun))) };
    close = vi.fn();
  });

  it('shows the dry run counts and row errors without closing', () => {
    const fixture = render();

    fixture.componentInstance.run(true);
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(api.importCsv).toHaveBeenCalledWith('p1', expect.any(File), true);
    expect(el.querySelector('[data-test-id="requirement-import-errors"]')?.textContent).toContain('unknown parentExternalId');
    expect(close).not.toHaveBeenCalled();
  });

  it('closes with the result of the real import', () => {
    const fixture = render();

    fixture.componentInstance.run(false);

    expect(close).toHaveBeenCalledWith(result(false));
  });
});
