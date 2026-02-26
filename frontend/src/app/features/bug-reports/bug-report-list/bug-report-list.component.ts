import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { AsyncPipe, DatePipe, LowerCasePipe } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { BugReportActions } from '../../../store/bug-report/bug-report.actions';
import { selectAllBugReports, selectBugReportsLoading } from '../../../store/bug-report/bug-report.selectors';
import { BugReport, BugReportStatus } from '../../../shared/models/bug-report.model';

@Component({
  selector: 'app-bug-report-list',
  standalone: true,
  imports: [
    AsyncPipe,
    DatePipe,
    LowerCasePipe,
    RouterLink,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatSelectModule,
    MatProgressSpinnerModule,
    TranslateModule,
  ],
  templateUrl: './bug-report-list.component.html',
  styleUrl: './bug-report-list.component.scss',
})
export class BugReportListComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);

  projectId = '';
  bugReports$ = this.store.select(selectAllBugReports);
  loading$ = this.store.select(selectBugReportsLoading);
  displayedColumns = ['title', 'priority', 'status', 'assignee', 'createdAt'];

  statusFilter: BugReportStatus | '' = '';
  allStatuses: BugReportStatus[] = ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'WONTFIX'];

  ngOnInit(): void {
    this.projectId = this.route.parent?.snapshot.paramMap.get('id') ?? '';
    if (this.projectId) {
      this.store.dispatch(BugReportActions.loadBugReports({ projectId: this.projectId }));
    }
  }

  filteredReports(reports: BugReport[]): BugReport[] {
    if (!this.statusFilter) return reports;
    return reports.filter((r) => r.status === this.statusFilter);
  }
}
