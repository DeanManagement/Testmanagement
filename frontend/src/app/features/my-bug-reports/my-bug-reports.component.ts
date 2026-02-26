import { Component, inject, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { AsyncPipe, DatePipe, LowerCasePipe } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { BugReportActions } from '../../store/bug-report/bug-report.actions';
import { selectMyBugReports, selectMyBugReportsLoading } from '../../store/bug-report/bug-report.selectors';

@Component({
  selector: 'app-my-bug-reports',
  standalone: true,
  imports: [
    AsyncPipe,
    DatePipe,
    LowerCasePipe,
    RouterLink,
    MatTableModule,
    MatIconModule,
    MatProgressSpinnerModule,
    TranslateModule,
  ],
  templateUrl: './my-bug-reports.component.html',
  styleUrl: './my-bug-reports.component.scss',
})
export class MyBugReportsComponent implements OnInit {
  private readonly store = inject(Store);

  myBugs$ = this.store.select(selectMyBugReports);
  loading$ = this.store.select(selectMyBugReportsLoading);
  displayedColumns = ['title', 'projectKey', 'priority', 'status', 'createdAt'];

  ngOnInit(): void {
    this.store.dispatch(BugReportActions.loadMyBugReports());
  }
}
