import { Component, inject, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { AsyncPipe, DatePipe, LowerCasePipe } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { TestRunActions } from '../../store/test-run/test-run.actions';
import { selectMyTestRuns, selectMyTestRunsLoading } from '../../store/test-run/test-run.selectors';

@Component({
  selector: 'app-my-test-runs',
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
  templateUrl: './my-test-runs.component.html',
  styleUrl: './my-test-runs.component.scss',
})
export class MyTestRunsComponent implements OnInit {
  private readonly store = inject(Store);

  myTestRuns$ = this.store.select(selectMyTestRuns);
  loading$ = this.store.select(selectMyTestRunsLoading);
  displayedColumns = ['key', 'name', 'projectKey', 'environment', 'status', 'createdAt'];

  ngOnInit(): void {
    this.store.dispatch(TestRunActions.loadMyTestRuns());
  }
}
