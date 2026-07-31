import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { AsyncPipe, LowerCasePipe } from '@angular/common';
import { CdkDragDrop, DragDropModule } from '@angular/cdk/drag-drop';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { BugReportActions } from '../../../store/bug-report/bug-report.actions';
import { selectAllBugReports, selectBugReportsLoading, selectBugReportsError } from '../../../store/bug-report/bug-report.selectors';
import { BugReport, BugReportStatus } from '../../../shared/models/bug-report.model';
import { LocalizedDatePipe } from '../../../shared/pipes/localized-date.pipe';

@Component({
  selector: 'app-bug-report-list',
  standalone: true,
  imports: [
    AsyncPipe,
    LocalizedDatePipe,
    LowerCasePipe,
    RouterLink,
    DragDropModule,
    MatTableModule,
    MatButtonModule,
    MatButtonToggleModule,
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
  error$ = this.store.select(selectBugReportsError);
  displayedColumns = ['title', 'priority', 'status', 'assignee', 'createdAt'];

  viewMode: 'list' | 'kanban' = 'list';
  statusFilter: BugReportStatus | '' = '';
  kanbanStatuses: BugReportStatus[] = ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'WONTFIX'];
  allStatuses: BugReportStatus[] = ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'WONTFIX'];

  get kanbanLaneIds(): string[] {
    return this.kanbanStatuses.map((s) => 'lane-' + s);
  }

  ngOnInit(): void {
    this.projectId = this.route.parent?.snapshot.paramMap.get('id') ?? '';
    if (this.projectId) {
      this.store.dispatch(BugReportActions.loadBugReports({ projectId: this.projectId }));
    }
  }

  retry(): void {
    this.store.dispatch(BugReportActions.loadBugReports({ projectId: this.projectId }));
  }

  filteredReports(reports: BugReport[]): BugReport[] {
    if (!this.statusFilter) return reports;
    return reports.filter((r) => r.status === this.statusFilter);
  }

  getBugsForStatus(reports: BugReport[], status: BugReportStatus): BugReport[] {
    return reports.filter((r) => r.status === status);
  }

  onCardDrop(event: CdkDragDrop<BugReport[]>, newStatus: BugReportStatus): void {
    const bug: BugReport = event.item.data;
    if (bug.status === newStatus) return;

    this.store.dispatch(
      BugReportActions.updateBugReport({
        projectId: this.projectId,
        id: bug.id,
        request: {
          title: bug.title,
          description: bug.description || undefined,
          stepsToReproduce: bug.stepsToReproduce || undefined,
          expectedBehavior: bug.expectedBehavior || undefined,
          actualBehavior: bug.actualBehavior || undefined,
          priority: bug.priority,
          status: newStatus,
          environment: bug.environment || undefined,
          testResultId: bug.testResultId || undefined,
          testRunId: bug.testRunId || undefined,
          assigneeId: bug.assigneeId || undefined,
        },
      })
    );
  }
}
