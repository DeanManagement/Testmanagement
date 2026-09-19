import { PlanDefectsComponent } from '../plan-defects/plan-defects.component';
import { ChangeDetectorRef, Component, DestroyRef, ElementRef, inject, OnInit, ViewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { AsyncPipe, DecimalPipe, formatDate, LowerCasePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatChipsModule } from '@angular/material/chips';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Observable, of } from 'rxjs';
import { distinctUntilChanged, filter, map, take } from 'rxjs/operators';
import {
  Chart,
  DoughnutController,
  BarController,
  ArcElement,
  BarElement,
  CategoryScale,
  LinearScale,
  LineController,
  LineElement,
  PointElement,
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
  DoughnutController, BarController, LineController,
  ArcElement, BarElement, LineElement, PointElement,
  CategoryScale, LinearScale, Tooltip, Legend
);

import { EnvironmentRollup, runsByEnvironment } from './runs-by-environment';
import { burnDownDates } from './burn-down-dates';
import { BurnDown } from '../../../shared/models/effort.model';
import { Readiness } from '../../../shared/models/readiness.model';
import { ReleaseReadinessCardComponent } from './release-readiness-card.component';
import { DurationPipe } from '../../../shared/pipes/duration.pipe';
import { LocalizedDatePipe } from '../../../shared/pipes/localized-date.pipe';

@Component({
  selector: 'app-test-plan-detail',
  standalone: true,
  imports: [
    PlanDefectsComponent,
    ReleaseReadinessCardComponent,
    DurationPipe,
    LocalizedDatePipe,
    MatSlideToggleModule,
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
  @ViewChild('burnDownLine') burnDownCanvas?: ElementRef<HTMLCanvasElement>;

  projectId = '';
  planId = '';
  testPlan$: Observable<TestPlan | undefined> = of(undefined);
  summary: TestPlanSummary | null = null;
  burnDown: BurnDown | null = null;
  readiness: Readiness | null = null;
  runColumns = ['name', 'environment', 'status', 'total', 'passed', 'failed'];
  environmentColumns = ['environment', 'runs', 'total', 'passed', 'failed', 'passRate'];
  groupByEnvironment = false;
  environmentRollups: EnvironmentRollup[] = [];

  private resultDoughnutChart: Chart | null = null;
  private runStatusBarChart: Chart | null = null;
  private passRateBarChart: Chart | null = null;
  private burnDownChart: Chart | null = null;

  ngOnInit(): void {
    this.projectId = this.route.parent?.snapshot.paramMap.get('id') ?? '';
    this.planId = this.route.snapshot.paramMap.get('planId') ?? '';

    if (this.projectId && this.planId) {
      this.store.dispatch(TestPlanActions.loadTestPlan({ projectId: this.projectId, id: this.planId }));
      this.testPlan$ = this.store.select(selectTestPlanById(this.planId));
      this.loadSummary();
      this.loadBurnDown();
      // The plan form saves and navigates here without waiting, so a readiness fetched on arrival
      // can predate the new gate. Fetching whenever the stored plan changes also covers that save.
      this.testPlan$.pipe(
        map((plan) => plan?.updatedAt),
        filter((updatedAt): updatedAt is string => !!updatedAt),
        distinctUntilChanged(),
        takeUntilDestroyed(this.destroyRef),
      ).subscribe(() => this.loadReadiness());
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
      this.environmentRollups = runsByEnvironment(summary.runs);
      this.cdr.detectChanges();
      this.renderCharts();
    });
  }

  private loadReadiness(): void {
    this.testPlanApi.getReadiness(this.projectId, this.planId).pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe((readiness) => {
        this.readiness = readiness;
        this.cdr.detectChanges();
      });
  }

  private loadBurnDown(): void {
    this.testPlanApi.getBurnDown(this.projectId, this.planId).pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe((burnDown) => {
        this.burnDown = burnDown;
        this.cdr.detectChanges();
        this.renderBurnDown();
      });
  }

  private renderCharts(): void {
    applyChartDefaults(Chart);
    this.renderResultDoughnut();
    this.renderRunStatusBar();
    this.renderPassRateBar();
    this.renderBurnDown();
  }

  /**
   * Actual remaining effort per day against an ideal line to the target date (PRD-036). Both
   * series share one date axis, so the actual line simply ends at today.
   */
  private renderBurnDown(): void {
    this.burnDownChart?.destroy();
    this.burnDownChart = null;
    if (!this.burnDownCanvas || !this.burnDown?.hasEstimates) return;
    applyChartDefaults(Chart);

    const dates = burnDownDates(this.burnDown);
    const actual = new Map(this.burnDown.days.map((p) => [p.date, p.remainingMinutes]));
    const ideal = new Map(this.burnDown.idealLine.map((p) => [p.date, p.remainingMinutes]));
    const locale = (this.translate.currentLang || 'en') === 'de' ? 'de-DE' : 'en-US';
    const toHours = (minutes: number | undefined) => (minutes === undefined ? null : Math.round(minutes / 6) / 10);

    this.burnDownChart = new Chart(this.burnDownCanvas.nativeElement, {
      type: 'line',
      data: {
        // A yyyy-MM-dd string parses as local midnight, so it must be formatted locally too: a UTC
        // formatter shows the day before anywhere east of Greenwich.
        labels: dates.map((date) => formatDate(date, 'd MMM', locale)),
        datasets: [
          {
            label: this.translate.instant('timeTracking.burnDown.remaining'),
            data: dates.map((date) => toHours(actual.get(date))),
            borderColor: '#2196f3',
            backgroundColor: '#2196f3',
            pointRadius: 2,
            tension: 0,
          },
          {
            label: this.translate.instant('timeTracking.burnDown.ideal'),
            data: dates.map((date) => toHours(ideal.get(date))),
            borderColor: '#9e9e9e',
            backgroundColor: '#9e9e9e',
            borderDash: [6, 4],
            pointRadius: 0,
            spanGaps: true,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { position: 'bottom' } },
        scales: {
          y: { beginAtZero: true, title: { display: true, text: this.translate.instant('timeTracking.burnDown.hours') } },
        },
      },
    });
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
          // Full run names rotated into the axis took the whole canvas and collapsed the plot;
          // the tooltip still shows the full name.
          x: {
            ticks: {
              maxRotation: 45,
              autoSkip: true,
              callback: (_value, index) => shortLabel(runs[index]?.name ?? ''),
            },
          },
          y: { beginAtZero: true, max: 100, ticks: { callback: (v) => v + '%' } },
        },
      },
    });
  }
}

const MAX_AXIS_LABEL_LENGTH = 16;

function shortLabel(name: string): string {
  return name.length > MAX_AXIS_LABEL_LENGTH ? name.slice(0, MAX_AXIS_LABEL_LENGTH - 1) + '…' : name;
}
