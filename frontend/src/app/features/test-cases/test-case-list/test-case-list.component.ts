import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { AsyncPipe, LowerCasePipe } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatChipsModule } from '@angular/material/chips';
import { TranslateModule } from '@ngx-translate/core';
import { TestCaseActions } from '../../../store/test-case/test-case.actions';
import { selectAllTestCases, selectTestCasesLoading } from '../../../store/test-case/test-case.selectors';

@Component({
  selector: 'app-test-case-list',
  standalone: true,
  imports: [
    AsyncPipe,
    LowerCasePipe,
    RouterLink,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatChipsModule,
    TranslateModule,
  ],
  templateUrl: './test-case-list.component.html',
  styleUrl: './test-case-list.component.scss',
})
export class TestCaseListComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);

  projectId = '';
  testCases$ = this.store.select(selectAllTestCases);
  loading$ = this.store.select(selectTestCasesLoading);
  displayedColumns = ['key', 'title', 'priority', 'status', 'labels', 'actions'];

  ngOnInit(): void {
    this.projectId = this.route.parent?.snapshot.paramMap.get('id') ?? '';
    if (this.projectId) {
      this.store.dispatch(TestCaseActions.loadTestCases({ projectId: this.projectId }));
    }
  }

  deleteTestCase(id: string): void {
    this.store.dispatch(TestCaseActions.deleteTestCase({ projectId: this.projectId, id }));
  }
}
