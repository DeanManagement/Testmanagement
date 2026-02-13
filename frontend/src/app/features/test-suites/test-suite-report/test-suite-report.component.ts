import { ChangeDetectorRef, Component, inject, OnInit, ViewChild, ElementRef } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { DatePipe, LowerCasePipe, DecimalPipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { TranslateModule } from '@ngx-translate/core';
import { Chart, DoughnutController, ArcElement, Tooltip, Legend } from 'chart.js';
import { TestSuiteReport } from '../../../shared/models/test-suite.model';
import { TestSuiteApiService } from '../../../core/services/test-suite-api.service';

Chart.register(DoughnutController, ArcElement, Tooltip, Legend);

@Component({
  selector: 'app-test-suite-report',
  standalone: true,
  imports: [
    DatePipe,
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

  @ViewChild('chartCanvas') chartCanvas!: ElementRef<HTMLCanvasElement>;

  projectId = '';
  suiteId = '';
  report: TestSuiteReport | null = null;
  displayedColumns = ['testCase', 'status', 'fromRun', 'date'];
  downloading = false;
  private chart: Chart | null = null;

  ngOnInit(): void {
    this.projectId = this.route.parent?.snapshot.paramMap.get('id') ?? '';
    this.suiteId = this.route.snapshot.paramMap.get('suiteId') ?? '';
    if (this.projectId && this.suiteId) {
      this.testSuiteApi.getReport(this.projectId, this.suiteId).subscribe(report => {
        this.report = report;
        this.cdr.detectChanges();
        this.renderChart();
      });
    }
  }

  downloadPdf(): void {
    this.downloading = true;
    this.testSuiteApi.downloadReportPdf(this.projectId, this.suiteId).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `test-suite-report-${this.report?.name ?? this.suiteId}.pdf`;
        a.click();
        URL.revokeObjectURL(url);
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
        labels: ['Passed', 'Failed', 'Blocked', 'Skipped', 'Untested'],
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
