import { ChangeDetectorRef, Component, inject, OnInit, ViewChild, ElementRef } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { DecimalPipe, KeyValuePipe, LowerCasePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
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
import { ProjectDashboard } from '../../../shared/models/dashboard.model';

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
    TranslateModule,
  ],
  templateUrl: './project-dashboard.component.html',
  styleUrl: './project-dashboard.component.scss',
})
export class ProjectDashboardComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly projectApi = inject(ProjectApiService);
  private readonly cdr = inject(ChangeDetectorRef);

  @ViewChild('statusChart') statusChartCanvas!: ElementRef<HTMLCanvasElement>;
  @ViewChild('priorityChart') priorityChartCanvas!: ElementRef<HTMLCanvasElement>;
  @ViewChild('resultsChart') resultsChartCanvas!: ElementRef<HTMLCanvasElement>;
  @ViewChild('trendChart') trendChartCanvas!: ElementRef<HTMLCanvasElement>;

  projectId = '';
  dashboard: ProjectDashboard | null = null;
  loading = true;
  recentRunColumns = ['name', 'environment', 'status', 'results'];

  private statusChart: Chart | null = null;
  private priorityChart: Chart | null = null;
  private resultsChart: Chart | null = null;
  private trendChart: Chart | null = null;

  ngOnInit(): void {
    this.projectId = this.route.snapshot.paramMap.get('id') ?? '';
    if (this.projectId) {
      this.projectApi.getDashboard(this.projectId).subscribe({
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
  }

  private renderCharts(): void {
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
        labels: Object.keys(data),
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
        labels: Object.keys(data),
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
        labels: Object.keys(data),
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
    if (trend.length === 0) return;

    this.trendChart = new Chart(this.trendChartCanvas.nativeElement, {
      type: 'line',
      data: {
        labels: trend.map(t => t.name),
        datasets: [{
          label: 'Pass Rate %',
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
}
