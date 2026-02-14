import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { AsyncPipe, LowerCasePipe } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { TestPlanActions } from '../../../store/test-plan/test-plan.actions';
import { selectAllTestPlans, selectTestPlansLoading } from '../../../store/test-plan/test-plan.selectors';

@Component({
  selector: 'app-test-plan-list',
  standalone: true,
  imports: [
    AsyncPipe,
    LowerCasePipe,
    RouterLink,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatProgressSpinnerModule,
    TranslateModule,
  ],
  templateUrl: './test-plan-list.component.html',
  styleUrl: './test-plan-list.component.scss',
})
export class TestPlanListComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);

  projectId = '';
  testPlans$ = this.store.select(selectAllTestPlans);
  loading$ = this.store.select(selectTestPlansLoading);
  displayedColumns = ['name', 'status', 'targetDate', 'testRunCount', 'actions'];

  ngOnInit(): void {
    this.projectId = this.route.parent?.snapshot.paramMap.get('id') ?? '';
    if (this.projectId) {
      this.store.dispatch(TestPlanActions.loadTestPlans({ projectId: this.projectId }));
    }
  }

  deleteTestPlan(id: string): void {
    this.store.dispatch(TestPlanActions.deleteTestPlan({ projectId: this.projectId, id }));
  }
}
