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
import { TestSuiteReport } from '../../../shared/models/test-suite.model';
import { TestSuiteApiService } from '../../../core/services/test-suite-api.service';

Chart.register(DoughnutController, ArcElement, Tooltip, Legend);

@Component({
  selector: 'app-test-suite-report',
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
  ],
  templateUrl: './test-suite-report.component.html',
  styleUrl: './test-suite-report.component.scss',
})
export class TestSuiteReportComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly testSuiteApi = inject(TestSuiteApiService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly translate = inject(TranslateService);
  private readonly destroyRef = inject(DestroyRef);

  @ViewChild('chartCanvas') chartCanvas!: ElementRef<HTMLCanvasElement>;

  projectId = '';
  suiteId = '';
  report: TestSuiteReport | null = null;
  displayedColumns = ['testCase', 'status', 'fromRun', 'date'];
  downloading = false;
  private chart: Chart | null = null;

  ngOnInit(): void {
    // Chart.js keeps canvas/resize handlers alive unless destroyed explicitly.
    this.destroyRef.onDestroy(() => this.chart?.destroy());
    this.projectId = this.route.parent?.snapshot.paramMap.get('id') ?? '';
    this.suiteId = this.route.snapshot.paramMap.get('suiteId') ?? '';
    if (this.projectId && this.suiteId) {
      this.testSuiteApi.getReport(this.projectId, this.suiteId)
        .pipe(take(1), takeUntilDestroyed(this.destroyRef))
        .subscribe(report => {
          this.report = report;
          this.cdr.detectChanges();
          this.renderChart();
        });
    }
    this.translate.onLangChange
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.renderChart());
  }

  downloadPdf(): void {
    this.downloading = true;
    this.testSuiteApi.downloadReportPdf(this.projectId, this.suiteId).pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `test-suite-report-${this.report?.name ?? this.suiteId}.pdf`;
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
    if (!this.chartCanvas || !this.report) return;
    this.chart?.destroy();

    this.chart = new Chart(this.chartCanvas.nativeElement, {
      type: 'doughnut',
      data: {
        labels: [
          this.translate.instant('report.passed'),
          this.translate.instant('report.failed'),
          this.translate.instant('report.blocked'),
          this.translate.instant('report.skipped'),
          this.translate.instant('report.untested'),
        ],
        datasets: [{
          data: [
            this.report.passed,
            this.report.failed,
            this.report.blocked,
            this.report.skipped,
            this.report.untested,
          ],
          backgroundColor: ['#4caf50', '#f44336', '#ff9800', '#9e9e9e', '#bdbdbd'],
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
