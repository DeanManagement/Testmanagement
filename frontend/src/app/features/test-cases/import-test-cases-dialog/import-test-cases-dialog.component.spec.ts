import { TestBed } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestCaseApiService } from '../../../core/services/test-case-api.service';
import { ImportResult } from '../../../shared/models/test-case.model';
import { TestCaseFolder } from '../../../shared/models/test-case-folder.model';
import { flattenFolders, ImportTestCasesDialogComponent } from './import-test-cases-dialog.component';

const RESULT: ImportResult = { imported: 1, skipped: 0, dryRun: true, errors: [], warnings: [], updated: 2, unchanged: 3 };

const folder = (id: string, children: TestCaseFolder[] = []): TestCaseFolder =>
  ({ id, name: id, parentId: null, sortOrder: 0, testCaseCount: 0, totalTestCaseCount: 0, children, createdAt: '', updatedAt: '' });

describe('ImportTestCasesDialogComponent', () => {
  let importFn: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    TestBed.resetTestingModule();
    importFn = vi.fn(() => of(RESULT));
    TestBed.configureTestingModule({
      imports: [ImportTestCasesDialogComponent, TranslateModule.forRoot()],
      providers: [
        { provide: TestCaseApiService, useValue: { import: importFn } },
        { provide: MatDialogRef, useValue: { close: vi.fn() } },
      ],
    });
  });

  function previewOf(fileName: string): HTMLElement {
    const fixture = TestBed.createComponent(ImportTestCasesDialogComponent);
    const dialog = fixture.componentInstance;
    dialog.projectId = 'proj';
    dialog.folderId = 'target';
    dialog.file = new File(['Feature: x'], fileName);
    dialog.dryRun();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  describe('when a Gherkin file is previewed', () => {
    it('should send the chosen folder and show updated and unchanged counts', () => {
      const el = previewOf('login.feature');

      expect(importFn).toHaveBeenCalledWith('proj', expect.any(File), true, 'target');
      expect(el.querySelector('[data-test-id="import-updated"]')?.textContent).toContain('2');
      expect(el.querySelector('[data-test-id="import-unchanged"]')?.textContent).toContain('3');
    });
  });

  describe('when a CSV file is previewed', () => {
    it('should not show counts that only mean something for Gherkin', () => {
      const el = previewOf('cases.csv');

      expect(el.querySelector('[data-test-id="import-updated"]')).toBeNull();
    });
  });

  describe('flattenFolders', () => {
    it('should list each folder under its parent, one level deeper', () => {
      const options = flattenFolders([folder('a', [folder('a1', [folder('a1x')])]), folder('b')]);

      expect(options.map((o) => `${o.id}:${o.depth}`)).toEqual(['a:0', 'a1:1', 'a1x:2', 'b:0']);
    });
  });
});
