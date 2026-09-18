import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { combineLatest, map, Observable } from 'rxjs';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
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
import { environmentNamesOf, filterByEnvironment } from './environment-filter';
import { TestRun } from '../../shared/models/test-run.model';

import { ExploratorySessionApiService } from '../../core/services/exploratory-session-api.service';

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
    MatFormFieldModule,
    MatSelectModule,
    FormsModule,
    TranslateModule,
  ],
  templateUrl: './my-test-runs.component.html',
  styleUrl: './my-test-runs.component.scss',
})
export class MyTestRunsComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  /** My planned and running exploratory sessions, across projects. */
  readonly mySessions$ = inject(ExploratorySessionApiService).assignedToMe();

  /** Bound to ?environment= like the other list filters (PRD-032). */
  readonly environmentFilter$ = this.route.queryParamMap.pipe(map((params) => params.get('environment') ?? ''));
  readonly environmentNames$ = combineLatest([
    this.store.select(selectMyInProgressTestRuns),
    this.store.select(selectMyPlannedTestRuns),
    this.store.select(selectMyCompletedTestRuns),
  ]).pipe(map((lists) => environmentNamesOf(lists.flat())));

  inProgressRuns$ = this.filtered(this.store.select(selectMyInProgressTestRuns));
  plannedRuns$ = this.filtered(this.store.select(selectMyPlannedTestRuns));
  activeLoading$ = this.store.select(selectMyActiveTestRunsLoading);
  completedRuns$ = this.filtered(this.store.select(selectMyCompletedTestRuns));
  completedLoading$ = this.store.select(selectMyCompletedTestRunsLoading);
  completedLoaded$ = this.store.select(selectMyCompletedTestRunsLoaded);

  displayedColumns = ['key', 'name', 'projectKey', 'environment', 'status', 'createdAt'];

  ngOnInit(): void {
    this.store.dispatch(TestRunActions.loadMyActiveTestRuns());
  }

  loadCompleted(): void {
    this.store.dispatch(TestRunActions.loadMyCompletedTestRuns());
  }

  setEnvironmentFilter(name: string): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { environment: name || null },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  private filtered(runs$: Observable<TestRun[]>): Observable<TestRun[]> {
    return combineLatest([runs$, this.environmentFilter$]).pipe(
      map(([runs, environment]) => filterByEnvironment(runs, environment)));
  }
}
