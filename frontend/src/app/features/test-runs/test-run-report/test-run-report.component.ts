import { ChangeDetectorRef, Component, DestroyRef, inject, OnInit, ViewChild, ElementRef } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { take } from 'rxjs/operators';
import { LowerCasePipe, DecimalPipe } from '@angular/common';
import { LocalizedDatePipe } from '../../../shared/pipes/localized-date.pipe';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Chart, DoughnutController, ArcElement, Tooltip, Legend } from 'chart.js';
import { TestResult, TestRunReport } from '../../../shared/models/test-run.model';
import { RunFailureSummaryComponent } from '../run-failure-summary/run-failure-summary.component';
import { failuresOf, worstFirst } from '../../../shared/utils/test-result-triage';
import { TestRunApiService } from '../../../core/services/test-run-api.service';
import { ThemeService } from '../../../core/services/theme.service';
import { applyChartDefaults } from '../../../core/utils/chart-theme';

Chart.register(DoughnutController, ArcElement, Tooltip, Legend);

@Component({
  selector: 'app-test-run-report',
  standalone: true,
  imports: [
    LocalizedDatePipe,
    LowerCasePipe,
    DecimalPipe,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatTableModule,
    TranslateModule,
    RunFailureSummaryComponent,
  ],
  templateUrl: './test-run-report.component.html',
  styleUrl: './test-run-report.component.scss',
})
export class TestRunReportComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly testRunApi = inject(TestRunApiService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly translate = inject(TranslateService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly themeService = inject(ThemeService);

  @ViewChild('chartCanvas') chartCanvas!: ElementRef<HTMLCanvasElement>;

  projectId = '';
  runId = '';
  report: TestRunReport | null = null;
  downloading = false;

  /** Shared with the run detail so the two screens cannot disagree about what counts as a failure. */
  get failures(): TestResult[] {
    return failuresOf(this.report?.results);
  }

  get resultsWorstFirst(): TestResult[] {
    return worstFirst(this.report?.results);
  }
  private chart: Chart | null = null;

  ngOnInit(): void {
    // Chart.js keeps canvas/resize handlers alive unless destroyed explicitly.
    this.destroyRef.onDestroy(() => this.chart?.destroy());
    this.projectId = this.route.parent?.snapshot.paramMap.get('id') ?? '';
    this.runId = this.route.snapshot.paramMap.get('runId') ?? '';
    if (this.projectId && this.runId) {
      this.testRunApi.getReport(this.projectId, this.runId)
        .pipe(take(1), takeUntilDestroyed(this.destroyRef))
        .subscribe(report => {
          this.report = report;
          this.cdr.detectChanges();
          this.renderChart();
        });
    }
    // Relabel the chart when the UI language changes.
    this.translate.onLangChange
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.renderChart());
    this.themeService.resolvedChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.renderChart());
  }

  downloadPdf(): void {
    this.downloading = true;
    this.testRunApi.downloadReportPdf(this.projectId, this.runId).pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `test-run-report-${this.report?.name ?? this.runId}.pdf`;
        a.click();
        // Revoke on the next macrotask so the browser has started the download.
        setTimeout(() => URL.revokeObjectURL(url));
        this.downloading = false;
      },
      error: () => {
        this.downloading = false;
      },
    });
  }

  private renderChart(): void {
    applyChartDefaults(Chart);
    if (!this.chartCanvas || !this.report) return;
    this.chart?.destroy();

    this.chart = new Chart(this.chartCanvas.nativeElement, {
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
          data: [
            this.report.passed,
            this.report.failed,
            this.report.blocked,
            this.report.skipped,
            this.report.pending,
          ],
          backgroundColor: ['#4caf50', '#f44336', '#ff9800', '#9e9e9e', '#2196f3'],
        }],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { position: 'bottom' },
        },
      },
    });
  }
}
