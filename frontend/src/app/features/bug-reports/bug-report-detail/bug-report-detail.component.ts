import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { AsyncPipe, DatePipe, LowerCasePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { TranslateModule } from '@ngx-translate/core';
import { Observable, of } from 'rxjs';
import { BugReportActions } from '../../../store/bug-report/bug-report.actions';
import { selectBugReportById } from '../../../store/bug-report/bug-report.selectors';
import { BugReport, BugReportStatus } from '../../../shared/models/bug-report.model';

@Component({
  selector: 'app-bug-report-detail',
  standalone: true,
  imports: [
    AsyncPipe,
    DatePipe,
    LowerCasePipe,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatSelectModule,
    MatFormFieldModule,
    TranslateModule,
  ],
  templateUrl: './bug-report-detail.component.html',
  styleUrl: './bug-report-detail.component.scss',
})
export class BugReportDetailComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);

  projectId = '';
  bugId = '';
  bugReport$: Observable<BugReport | undefined> = of(undefined);
  statuses: BugReportStatus[] = ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'WONTFIX'];

  ngOnInit(): void {
    this.projectId = this.route.parent?.snapshot.paramMap.get('id') ?? '';
    this.bugId = this.route.snapshot.paramMap.get('bugId') ?? '';
    if (this.projectId && this.bugId) {
      this.store.dispatch(BugReportActions.loadBugReport({ projectId: this.projectId, id: this.bugId }));
      this.bugReport$ = this.store.select(selectBugReportById(this.bugId));
    }
  }

  onStatusChange(bug: BugReport, status: BugReportStatus): void {
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
          status,
          environment: bug.environment || undefined,
          testResultId: bug.testResultId || undefined,
          testRunId: bug.testRunId || undefined,
          assigneeId: bug.assigneeId || undefined,
        },
      })
    );
  }

  deleteBugReport(id: string): void {
    this.store.dispatch(BugReportActions.deleteBugReport({ projectId: this.projectId, id }));
  }
}
