import { ChangeDetectorRef, Component, DestroyRef, ElementRef, inject, OnInit, ViewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { AsyncPipe, DecimalPipe, LowerCasePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatChipsModule } from '@angular/material/chips';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Observable, of } from 'rxjs';
import { take } from 'rxjs/operators';
import {
  Chart,
  DoughnutController,
  BarController,
  ArcElement,
  BarElement,
  CategoryScale,
  LinearScale,
  Tooltip,
  Legend,
} from 'chart.js';
import { TestPlanActions } from '../../../store/test-plan/test-plan.actions';
import { selectTestPlanById } from '../../../store/test-plan/test-plan.selectors';
import { TestPlan, TestPlanSummary } from '../../../shared/models/test-plan.model';
import { TestPlanApiService } from '../../../core/services/test-plan-api.service';
import { ThemeService } from '../../../core/services/theme.service';
import { applyChartDefaults } from '../../../core/utils/chart-theme';
import { EntityHistoryComponent } from '../../../shared/components/entity-history/entity-history.component';
import { WatchToggleComponent } from '../../../shared/components/watch-toggle/watch-toggle.component';

Chart.register(
  DoughnutController, BarController,
  ArcElement, BarElement,
  CategoryScale, LinearScale, Tooltip, Legend
);

@Component({
  selector: 'app-test-plan-detail',
  standalone: true,
  imports: [
    AsyncPipe,
    DecimalPipe,
    LowerCasePipe,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatTableModule,
    MatChipsModule,
    TranslateModule,
    EntityHistoryComponent,
    WatchToggleComponent,
  ],
  templateUrl: './test-plan-detail.component.html',
  styleUrl: './test-plan-detail.component.scss',
})
export class TestPlanDetailComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);
  private readonly testPlanApi = inject(TestPlanApiService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly translate = inject(TranslateService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly themeService = inject(ThemeService);

  @ViewChild('resultDoughnut') resultDoughnutCanvas!: ElementRef<HTMLCanvasElement>;
  @ViewChild('runStatusBar') runStatusBarCanvas!: ElementRef<HTMLCanvasElement>;
  @ViewChild('passRateBar') passRateBarCanvas!: ElementRef<HTMLCanvasElement>;

  projectId = '';
  planId = '';
  testPlan$: Observable<TestPlan | undefined> = of(undefined);
  summary: TestPlanSummary | null = null;
  runColumns = ['name', 'environment', 'status', 'total', 'passed', 'failed'];

  private resultDoughnutChart: Chart | null = null;
  private runStatusBarChart: Chart | null = null;
  private passRateBarChart: Chart | null = null;

  ngOnInit(): void {
    this.projectId = this.route.parent?.snapshot.paramMap.get('id') ?? '';
    this.planId = this.route.snapshot.paramMap.get('planId') ?? '';

    if (this.projectId && this.planId) {
      this.store.dispatch(TestPlanActions.loadTestPlan({ projectId: this.projectId, id: this.planId }));
      this.testPlan$ = this.store.select(selectTestPlanById(this.planId));
      this.loadSummary();
    }
    this.translate.onLangChange.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      if (this.summary) {
        this.renderCharts();
      }
    });
    this.themeService.resolvedChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      if (this.summary) {
        this.renderCharts();
      }
    });
  }

  deleteTestPlan(id: string): void {
    this.store.dispatch(TestPlanActions.deleteTestPlan({ projectId: this.projectId, id }));
  }

  private loadSummary(): void {
    this.testPlanApi.getSummary(this.projectId, this.planId).pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe((summary) => {
      this.summary = summary;
      this.cdr.detectChanges();
      this.renderCharts();
    });
  }

  private renderCharts(): void {
    applyChartDefaults(Chart);
    this.renderResultDoughnut();
    this.renderRunStatusBar();
    this.renderPassRateBar();
  }

  private renderResultDoughnut(): void {
    if (!this.resultDoughnutCanvas || !this.summary) return;
    this.resultDoughnutChart?.destroy();

    const { passed, failed, blocked, skipped, pending } = this.summary;
    if (passed + failed + blocked + skipped + pending === 0) return;

    this.resultDoughnutChart = new Chart(this.resultDoughnutCanvas.nativeElement, {
      type: 'doughnut',
      data: {
        labels: [
          this.translate.instant('resultStatus.PASSED'),
          this.translate.instant('resultStatus.FAILED'),
          this.translate.instant('resultStatus.BLOCKED'),
          this.translate.instant('resultStatus.SKIPPED'),
          this.translate.instant('resultStatus.PENDING'),
        ],
        datasets: [{
          data: [passed, failed, blocked, skipped, pending],
          backgroundColor: ['#4caf50', '#f44336', '#ff9800', '#9e9e9e', '#2196f3'],
        }],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { position: 'bottom' } },
      },
    });
  }

  private renderRunStatusBar(): void {
    if (!this.runStatusBarCanvas || !this.summary) return;
    this.runStatusBarChart?.destroy();

    const runs = this.summary.runs;
    if (runs.length === 0) return;

    const statusCounts: Record<string, number> = {};
    for (const run of runs) {
      statusCounts[run.status] = (statusCounts[run.status] || 0) + 1;
    }

    const statusColors: Record<string, string> = {
      PLANNED: '#2196f3',
      IN_PROGRESS: '#ff9800',
      COMPLETED: '#4caf50',
      ABORTED: '#f44336',
    };

    this.runStatusBarChart = new Chart(this.runStatusBarCanvas.nativeElement, {
      type: 'bar',
      data: {
        labels: Object.keys(statusCounts).map(k => this.translate.instant('testRun.status.' + k)),
        datasets: [{
          data: Object.values(statusCounts),
          backgroundColor: Object.keys(statusCounts).map(k => statusColors[k] || '#9e9e9e'),
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

  private renderPassRateBar(): void {
    if (!this.passRateBarCanvas || !this.summary) return;
    this.passRateBarChart?.destroy();

    const runs = this.summary.runs.filter(r => r.total > 0);
    if (runs.length === 0) return;

    this.passRateBarChart = new Chart(this.passRateBarCanvas.nativeElement, {
      type: 'bar',
      data: {
        labels: runs.map(r => r.name),
        datasets: [{
          label: this.translate.instant('testPlan.detail.passRate'),
          data: runs.map(r => Math.round(r.passed * 10000 / r.total) / 100),
          backgroundColor: '#4caf50',
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
