import { ChangeDetectorRef, Component, DestroyRef, inject, OnInit, ViewChild, ElementRef } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { take } from 'rxjs/operators';
import { DecimalPipe, KeyValuePipe, LowerCasePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import {
  Chart,
  DoughnutController,
  BarController,
  LineController,
  ArcElement,
  BarElement,
  LineElement,
  PointElement,
  CategoryScale,
  LinearScale,
  Tooltip,
  Legend,
} from 'chart.js';
import { ProjectApiService } from '../../../core/services/project-api.service';
import { ThemeService } from '../../../core/services/theme.service';
import { applyChartDefaults } from '../../../core/utils/chart-theme';
import { FlakyTest, ProjectDashboard } from '../../../shared/models/dashboard.model';

Chart.register(
  DoughnutController, BarController, LineController,
  ArcElement, BarElement, LineElement, PointElement,
  CategoryScale, LinearScale, Tooltip, Legend
);

@Component({
  selector: 'app-project-dashboard',
  standalone: true,
  imports: [
    DecimalPipe,
    KeyValuePipe,
    LowerCasePipe,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatTableModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    TranslateModule,
  ],
  templateUrl: './project-dashboard.component.html',
  styleUrl: './project-dashboard.component.scss',
})
export class ProjectDashboardComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly projectApi = inject(ProjectApiService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly translate = inject(TranslateService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly themeService = inject(ThemeService);

  @ViewChild('statusChart') statusChartCanvas!: ElementRef<HTMLCanvasElement>;
  @ViewChild('priorityChart') priorityChartCanvas!: ElementRef<HTMLCanvasElement>;
  @ViewChild('resultsChart') resultsChartCanvas!: ElementRef<HTMLCanvasElement>;
  @ViewChild('trendChart') trendChartCanvas!: ElementRef<HTMLCanvasElement>;

  projectId = '';
  dashboard: ProjectDashboard | null = null;
  /** Empty is the common case and reads as "nothing to worry about" rather than an error. */
  flakyTests: FlakyTest[] = [];
  loading = true;
  recentRunColumns = ['name', 'environment', 'status', 'results'];

  private statusChart: Chart | null = null;
  private priorityChart: Chart | null = null;
  private resultsChart: Chart | null = null;
  private trendChart: Chart | null = null;

  ngOnInit(): void {
    this.projectId = this.route.snapshot.paramMap.get('id') ?? '';
    if (this.projectId) {
      this.projectApi.getDashboard(this.projectId).pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe({
        next: (data) => {
          this.dashboard = data;
          this.loading = false;
          this.cdr.detectChanges();
          this.renderCharts();
        },
        error: () => {
          this.loading = false;
        },
      });
    }
    if (this.projectId) {
      this.projectApi.getFlakyTests(this.projectId, 5)
        .pipe(take(1), takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: (flaky) => {
            this.flakyTests = flaky;
            this.cdr.detectChanges();
          },
          // Analytics are a nice-to-have on this screen; a failure here must not blank the
          // dashboard that already loaded.
          error: () => undefined,
        });
    }

    this.translate.onLangChange.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      if (this.dashboard) {
        this.renderCharts();
      }
    });
    this.themeService.resolvedChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      if (this.dashboard) {
        this.renderCharts();
      }
    });
  }

  private renderCharts(): void {
    applyChartDefaults(Chart);
    this.renderStatusChart();
    this.renderPriorityChart();
    this.renderResultsChart();
    this.renderTrendChart();
  }

  private renderStatusChart(): void {
    if (!this.statusChartCanvas || !this.dashboard) return;
    this.statusChart?.destroy();

    const data = this.dashboard.testCasesByStatus;
    const statusColors: Record<string, string> = {
      DRAFT: '#9e9e9e',
      ACTIVE: '#4caf50',
      DEPRECATED: '#ff9800',
    };

    this.statusChart = new Chart(this.statusChartCanvas.nativeElement, {
      type: 'doughnut',
      data: {
        labels: Object.keys(data).map(k => this.translate.instant('testCaseStatus.' + k)),
        datasets: [{
          data: Object.values(data),
          backgroundColor: Object.keys(data).map(k => statusColors[k] || '#2196f3'),
        }],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { position: 'bottom' } },
      },
    });
  }

  private renderPriorityChart(): void {
    if (!this.priorityChartCanvas || !this.dashboard) return;
    this.priorityChart?.destroy();

    const data = this.dashboard.testCasesByPriority;
    const priorityColors: Record<string, string> = {
      LOW: '#4caf50',
      MEDIUM: '#2196f3',
      HIGH: '#ff9800',
      CRITICAL: '#f44336',
    };

    this.priorityChart = new Chart(this.priorityChartCanvas.nativeElement, {
      type: 'bar',
      data: {
        labels: Object.keys(data).map(k => this.translate.instant('priority.' + k)),
        datasets: [{
          data: Object.values(data),
          backgroundColor: Object.keys(data).map(k => priorityColors[k] || '#9e9e9e'),
        }],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: {
          y: { beginAtZero: true, ticks: { stepSize: 1 } },
        },
      },
    });
  }

  private renderResultsChart(): void {
    if (!this.resultsChartCanvas || !this.dashboard) return;
    this.resultsChart?.destroy();

    const data = this.dashboard.latestResultsByStatus;
    if (!data || Object.keys(data).length === 0) return;

    const resultColors: Record<string, string> = {
      PASSED: '#4caf50',
      FAILED: '#f44336',
      BLOCKED: '#ff9800',
      SKIPPED: '#9e9e9e',
      PENDING: '#2196f3',
    };

    this.resultsChart = new Chart(this.resultsChartCanvas.nativeElement, {
      type: 'doughnut',
      data: {
        labels: Object.keys(data).map(k => this.translate.instant('resultStatus.' + k)),
        datasets: [{
          data: Object.values(data),
          backgroundColor: Object.keys(data).map(k => resultColors[k] || '#bdbdbd'),
        }],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { position: 'bottom' } },
      },
    });
  }

  private renderTrendChart(): void {
    if (!this.trendChartCanvas || !this.dashboard) return;
    this.trendChart?.destroy();

    const trend = this.dashboard.passRateTrend;
    // A single completed run renders as a lone dot pinned to a corner; the
    // template shows a hint instead until there are two points to connect.
    if (trend.length < 2) return;

    this.trendChart = new Chart(this.trendChartCanvas.nativeElement, {
      type: 'line',
      data: {
        labels: trend.map(t => t.name),
        datasets: [{
          label: this.translate.instant('report.passRate'),
          data: trend.map(t => t.passRate),
          borderColor: '#4caf50',
          backgroundColor: 'rgba(76, 175, 80, 0.1)',
          fill: true,
          tension: 0.3,
        }],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: {
          y: { beginAtZero: true, max: 100, ticks: { callback: (v) => v + '%' } },
        },
      },
    });
  }

  /** Score as a percentage for the bar width and the label. */
  scorePercent(test: FlakyTest): number {
    return Math.round(test.flakyScore * 100);
  }

}