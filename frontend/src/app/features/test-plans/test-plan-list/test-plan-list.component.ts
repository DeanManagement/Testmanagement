import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Store } from '@ngrx/store';
import { AsyncPipe, LowerCasePipe } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { TestPlanActions } from '../../../store/test-plan/test-plan.actions';
import { selectAllTestPlans, selectTestPlansLoading } from '../../../store/test-plan/test-plan.selectors';
import { TestPlan, TestPlanStatus } from '../../../shared/models/test-plan.model';

@Component({
  selector: 'app-test-plan-list',
  standalone: true,
  imports: [
    AsyncPipe,
    LowerCasePipe,
    RouterLink,
    FormsModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
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
  searchTerm = '';
  statusFilter: TestPlanStatus | '' = '';
  allStatuses: TestPlanStatus[] = ['OPEN', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'];

  ngOnInit(): void {
    this.projectId = this.route.parent?.snapshot.paramMap.get('id') ?? '';
    if (this.projectId) {
      this.store.dispatch(TestPlanActions.loadTestPlans({ projectId: this.projectId }));
    }
  }

  filteredTestPlans(testPlans: TestPlan[]): TestPlan[] {
    let result = testPlans;
    if (this.searchTerm) {
      const term = this.searchTerm.toLowerCase();
      result = result.filter(p => p.name.toLowerCase().includes(term));
    }
    if (this.statusFilter) {
      result = result.filter(p => p.status === this.statusFilter);
    }
    return result;
  }

  deleteTestPlan(id: string): void {
    this.store.dispatch(TestPlanActions.deleteTestPlan({ projectId: this.projectId, id }));
  }
}
