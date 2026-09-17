import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { describe, expect, it, vi } from 'vitest';
import { CompletionInfo } from '../../../shared/models/test-run.model';
import { CompleteTestRunDialogComponent } from './complete-test-run-dialog.component';

function render(data: CompletionInfo): string {
  TestBed.configureTestingModule({
    imports: [CompleteTestRunDialogComponent, TranslateModule.forRoot()],
    providers: [
      { provide: MAT_DIALOG_DATA, useValue: data },
      { provide: MatDialogRef, useValue: { close: vi.fn() } },
    ],
  });
  const translate = TestBed.inject(TranslateService);
  // Shape, not wording: the spec pins which numbers reach the sentence, the catalogue owns the prose.
  translate.setTranslation('en', {
    testRun: { complete: { warning: 'f={{failed}} b={{blocked}} s={{skipped}} p={{pending}}', allPassed: 'ALL PASSED' } },
  });
  translate.use('en');
  const fixture = TestBed.createComponent(CompleteTestRunDialogComponent);
  fixture.detectChanges();
  return (fixture.nativeElement as HTMLElement).querySelector('.message span')?.textContent?.trim() ?? '';
}

const info = (over: Partial<CompletionInfo>): CompletionInfo =>
  ({ total: 20, passed: 20, failed: 0, blocked: 0, skipped: 0, pending: 0, worstStatus: 'PASSED', ...over });

describe('CompleteTestRunDialogComponent', () => {
  describe('when every result passed', () => {
    it('should say so', () => {
      expect(render(info({}))).toBe('ALL PASSED');
    });
  });

  describe('when results failed and one is still pending', () => {
    it('should report the pending result alongside the failures', () => {
      expect(render(info({ passed: 15, failed: 4, pending: 1, worstStatus: 'FAILED' }))).toBe('f=4 b=0 s=0 p=1');
    });
  });

  describe('when nothing failed but results were never executed', () => {
    it('should still warn, naming how many are pending', () => {
      expect(render(info({ passed: 18, pending: 2, worstStatus: 'PENDING' }))).toBe('f=0 b=0 s=0 p=2');
    });
  });
});
