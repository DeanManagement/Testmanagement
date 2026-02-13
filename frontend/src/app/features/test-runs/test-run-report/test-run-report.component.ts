import { ChangeDetectorRef, Component, inject, OnInit, ViewChild, ElementRef } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { DatePipe, LowerCasePipe, DecimalPipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { TranslateModule } from '@ngx-translate/core';
import { Chart, DoughnutController, ArcElement, Tooltip, Legend } from 'chart.js';
import { TestRunReport } from '../../../shared/models/test-run.model';
import { TestRunApiService } from '../../../core/services/test-run-api.service';

Chart.register(DoughnutController, ArcElement, Tooltip, Legend);

@Component({
  selector: 'app-test-run-report',
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
  templateUrl: './test-run-report.component.html',
  styleUrl: './test-run-report.component.scss',
})
export class TestRunReportComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly testRunApi = inject(TestRunApiService);
  private readonly cdr = inject(ChangeDetectorRef);

  @ViewChild('chartCanvas') chartCanvas!: ElementRef<HTMLCanvasElement>;

  projectId = '';
  runId = '';
  report: TestRunReport | null = null;
  downloading = false;
  private chart: Chart | null = null;

  ngOnInit(): void {
    this.projectId = this.route.parent?.snapshot.paramMap.get('id') ?? '';
    this.runId = this.route.snapshot.paramMap.get('runId') ?? '';
    if (this.projectId && this.runId) {
      this.testRunApi.getReport(this.projectId, this.runId).subscribe(report => {
        this.report = report;
        this.cdr.detectChanges();
        this.renderChart();
      });
    }
  }

  downloadPdf(): void {
    this.downloading = true;
    this.testRunApi.downloadReportPdf(this.projectId, this.runId).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `test-run-report-${this.report?.name ?? this.runId}.pdf`;
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
        labels: ['Passed', 'Failed', 'Blocked', 'Skipped', 'Pending'],
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
