import { Component, inject, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { AsyncPipe, LowerCasePipe } from '@angular/common';
import { LocalizedDatePipe } from '../../shared/pipes/localized-date.pipe';
import { MatTableModule } from '@angular/material/table';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { TestRunActions } from '../../store/test-run/test-run.actions';
import {
  selectMyInProgressTestRuns,
  selectMyPlannedTestRuns,
  selectMyActiveTestRunsLoading,
  selectMyCompletedTestRuns,
  selectMyCompletedTestRunsLoading,
  selectMyCompletedTestRunsLoaded,
} from '../../store/test-run/test-run.selectors';

@Component({
  selector: 'app-my-test-runs',
  standalone: true,
  imports: [
    AsyncPipe,
    LocalizedDatePipe,
    LowerCasePipe,
    RouterLink,
    MatTableModule,
    MatIconModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    TranslateModule,
  ],
  templateUrl: './my-test-runs.component.html',
  styleUrl: './my-test-runs.component.scss',
})
export class MyTestRunsComponent implements OnInit {
  private readonly store = inject(Store);

  inProgressRuns$ = this.store.select(selectMyInProgressTestRuns);
  plannedRuns$ = this.store.select(selectMyPlannedTestRuns);
  activeLoading$ = this.store.select(selectMyActiveTestRunsLoading);
  completedRuns$ = this.store.select(selectMyCompletedTestRuns);
  completedLoading$ = this.store.select(selectMyCompletedTestRunsLoading);
  completedLoaded$ = this.store.select(selectMyCompletedTestRunsLoaded);

  displayedColumns = ['key', 'name', 'projectKey', 'environment', 'status', 'createdAt'];

  ngOnInit(): void {
    this.store.dispatch(TestRunActions.loadMyActiveTestRuns());
  }

  loadCompleted(): void {
    this.store.dispatch(TestRunActions.loadMyCompletedTestRuns());
  }
}
