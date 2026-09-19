import { afterRenderEffect, Component, DestroyRef, ElementRef, inject, Input, OnDestroy, OnInit, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { take } from 'rxjs/operators';
import { BarController, BarElement, CategoryScale, Chart, Legend, LinearScale, LineController, LineElement,
  PointElement, Tooltip } from 'chart.js';
import { BugReportApiService } from '../../../../core/services/bug-report-api.service';
import { applyChartDefaults } from '../../../../core/utils/chart-theme';
import { ThemeService } from '../../../../core/services/theme.service';
import { DefectDashboard, OPEN_BUG_STATUSES, Priority } from '../../../../shared/models/bug-report.model';
import { LocalizedDatePipe } from '../../../../shared/pipes/localized-date.pipe';

Chart.register(BarController, LineController, BarElement, LineElement, PointElement, CategoryScale, LinearScale,
  Tooltip, Legend);

const PRIORITY_COLORS: Record<Priority, string> = {
  LOW: '#4caf50', MEDIUM: '#2196f3', HIGH: '#ff9800', CRITICAL: '#f44336',
};
const CREATED_COLOR = '#f44336';
const RESOLVED_COLOR = '#4caf50';

/**
 * Open defects by priority and reported-versus-resolved per week (PRD-047). Loads on its own, so
 * the main dashboard stays cheap; renders nothing when the project has bug reports switched off.
 */
@Component({
  selector: 'app-defect-dashboard',
  standalone: true,
  imports: [RouterLink, MatIconModule, TranslateModule],
  templateUrl: './defect-dashboard.component.html',
  styleUrl: './defect-dashboard.component.scss',
})
export class DefectDashboardComponent implements OnInit, OnDestroy {
  private readonly api = inject(BugReportApiService);
  private readonly translate = inject(TranslateService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly themeService = inject(ThemeService);
  private readonly datePipe = new LocalizedDatePipe();

  @Input({ required: true }) projectId!: string;
  readonly priorityCanvas = viewChild<ElementRef<HTMLCanvasElement>>('priorityChart');
  readonly trendCanvas = viewChild<ElementRef<HTMLCanvasElement>>('trendChart');

  readonly defects = signal<DefectDashboard | null>(null);
  readonly openStatuses = OPEN_BUG_STATUSES;
  /** Bumped on a theme switch: chart colors come from the theme when drawn. */
  private readonly themeVersion = signal(0);
  private charts: Chart[] = [];

  constructor() {
    // TES-BUG-20: the charts were drawn from ngAfterViewChecked, which a zoneless, signal-driven
    // refresh of this component alone never runs, so they stayed empty. An effect that runs after
    // rendering follows the data, the canvases and the theme instead.
    afterRenderEffect(() => {
      const defects = this.defects();
      const priority = this.priorityCanvas();
      const trend = this.trendCanvas();
      this.themeVersion();
      this.destroyCharts();
      if (!defects || !priority || !trend) {
        return;
      }
      applyChartDefaults(Chart);
      this.charts = [this.priorityChart(defects, priority), this.trendChart(defects, trend)];
    });
  }

  ngOnInit(): void {
    this.api.getDefectDashboard(this.projectId).pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (defects) => this.defects.set(defects), error: () => this.defects.set(null) });
    this.themeService.resolvedChanges.pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.themeVersion.update((version) => version + 1));
  }

  ngOnDestroy(): void {
    this.destroyCharts();
  }

  private destroyCharts(): void {
    this.charts.forEach((chart) => chart.destroy());
    this.charts = [];
  }

  private priorityChart(defects: DefectDashboard, canvas: ElementRef<HTMLCanvasElement>): Chart {
    const priorities = Object.keys(defects.openByPriority) as Priority[];
    return new Chart(canvas.nativeElement, {
      type: 'bar',
      data: {
        labels: priorities.map((p) => this.translate.instant('priority.' + p)),
        datasets: [{
          data: priorities.map((p) => defects.openByPriority[p]),
          backgroundColor: priorities.map((p) => PRIORITY_COLORS[p]),
        }],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: { y: { beginAtZero: true, ticks: { stepSize: 1 } } },
      },
    });
  }

  private trendChart(defects: DefectDashboard, canvas: ElementRef<HTMLCanvasElement>): Chart {
    return new Chart(canvas.nativeElement, {
      type: 'line',
      data: {
        labels: defects.trend.map((week) => this.datePipe.transform(week.weekStart, 'MMM d')),
        datasets: [
          { label: this.translate.instant('projectDashboard.defects.created'), data: defects.trend.map((w) => w.created),
            borderColor: CREATED_COLOR, backgroundColor: CREATED_COLOR, tension: 0.2 },
          { label: this.translate.instant('projectDashboard.defects.resolved'), data: defects.trend.map((w) => w.resolved),
            borderColor: RESOLVED_COLOR, backgroundColor: RESOLVED_COLOR, tension: 0.2 },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { position: 'bottom' } },
        scales: { y: { beginAtZero: true, ticks: { stepSize: 1 } } },
      },
    });
  }
}
