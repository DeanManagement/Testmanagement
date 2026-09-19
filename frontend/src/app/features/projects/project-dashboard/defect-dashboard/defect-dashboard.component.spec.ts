import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { Subject } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { DefectDashboardComponent } from './defect-dashboard.component';
import { BugReportApiService } from '../../../../core/services/bug-report-api.service';
import { DefectDashboard } from '../../../../shared/models/bug-report.model';

/** Placed the way the project dashboard places it: a child of an OnPush parent. */
@Component({
  standalone: true,
  imports: [DefectDashboardComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<app-defect-dashboard projectId="p1" />',
})
class HostComponent {
}

const DEFECTS: DefectDashboard = {
  open: 2,
  byStatus: { NEW: 1, OPEN: 1, IN_PROGRESS: 0, RESOLVED: 0, CLOSED: 0 },
  openByPriority: { LOW: 0, MEDIUM: 1, HIGH: 1, CRITICAL: 0 },
  trend: [{ weekStart: '2026-09-14', created: 2, resolved: 0 }],
};

/**
 * TES-BUG-20: the two defect charts stayed empty on production. Their data arrives after the first
 * render, and the drawing must follow it; jsdom cannot draw on a canvas, so the chart factories are
 * observed instead.
 */
describe('DefectDashboardComponent', () => {
  afterEach(() => vi.restoreAllMocks());

  it('draws both charts once the defects arrive after the first render', async () => {
    const response = new Subject<DefectDashboard>();
    const fakeChart = { destroy: vi.fn() };
    // The chart factories are private; reached through the prototype only to observe the drawing.
    const factories = DefectDashboardComponent.prototype as unknown as Record<'priorityChart' | 'trendChart', () => unknown>;
    const drawPriority = vi.spyOn(factories, 'priorityChart').mockReturnValue(fakeChart);
    const drawTrend = vi.spyOn(factories, 'trendChart').mockReturnValue(fakeChart);
    TestBed.configureTestingModule({
      imports: [HostComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: BugReportApiService, useValue: { getDefectDashboard: () => response } }],
    });
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    await fixture.whenStable();

    response.next(DEFECTS);
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('[data-test-id="defect-dashboard"]')).not.toBeNull();
    expect(drawPriority).toHaveBeenCalledTimes(1);
    expect(drawTrend).toHaveBeenCalledTimes(1);
  });
});
