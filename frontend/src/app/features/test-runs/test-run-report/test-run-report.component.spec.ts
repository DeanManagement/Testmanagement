import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestRunReportComponent } from './test-run-report.component';
import { TestRunApiService } from '../../../core/services/test-run-api.service';
import { TestRunReport } from '../../../shared/models/test-run.model';

/** PRD-048: the report names the plan and who ran each case, and the PDF follows the options. */
describe('TestRunReportComponent', () => {
  let downloadReportPdf: ReturnType<typeof vi.fn>;

  const report = {
    id: 'r1', name: 'Nightly', status: 'COMPLETED', environment: null, startTime: null, endTime: null,
    total: 1, passed: 1, failed: 0, blocked: 0, skipped: 0, pending: 0, passRate: 100, unapprovedResultIds: [],
    effort: { estimatedMinutes: 0, remainingMinutes: 0, pendingUnestimated: 0, actualMinutes: 0 },
    testPlanId: 'pl1', testPlanName: '2.1',
    results: [{
      id: 'res1', testCaseId: 'c1', testCaseKey: 'SPI-7', testCaseTitle: 'Checkout', status: 'PASSED',
      executedBy: 'u1', executedByName: 'Tess', executedAt: '2026-09-19T10:00:00Z', executedVersion: 3,
      comment: '', defectLink: null, stepResults: [],
    }],
  } as unknown as TestRunReport;

  beforeEach(() => {
    TestBed.resetTestingModule();
    downloadReportPdf = vi.fn(() => of(new Blob()));
    TestBed.configureTestingModule({
      imports: [TestRunReportComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        { provide: TestRunApiService, useValue: { getReport: () => of(report), downloadReportPdf, getScreenshotUrl: () => '' } },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { paramMap: convertToParamMap({ runId: 'r1' }) },
            parent: { snapshot: { paramMap: convertToParamMap({ id: 'p1' }) } },
          },
        },
      ],
    });
    globalThis.URL.createObjectURL = vi.fn(() => 'blob:x');
    globalThis.URL.revokeObjectURL = vi.fn();
  });

  it('shows the plan, the case key and who executed it', () => {
    const fixture = TestBed.createComponent(TestRunReportComponent);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;

    expect(el.querySelector('[data-test-id="report-plan"]')?.textContent).toContain('2.1');
    expect(el.textContent).toContain('SPI-7');
    expect(el.textContent).toContain('Tess');
  });

  it('asks the PDF for steps and screenshots as chosen, screenshots only with steps', () => {
    const fixture = TestBed.createComponent(TestRunReportComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.showScreenshots = true;
    component.downloadPdf();
    expect(downloadReportPdf).toHaveBeenLastCalledWith('p1', 'r1', { steps: false, screenshots: false });

    component.showSteps = true;
    component.downloadPdf();
    expect(downloadReportPdf).toHaveBeenLastCalledWith('p1', 'r1', { steps: true, screenshots: true });
  });
});
