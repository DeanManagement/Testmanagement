import { ChangeDetectorRef, Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { AsyncPipe, DecimalPipe, LowerCasePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatChipsModule } from '@angular/material/chips';
import { TranslateModule } from '@ngx-translate/core';
import { Observable, of } from 'rxjs';
import { TestPlanActions } from '../../../store/test-plan/test-plan.actions';
import { selectTestPlanById } from '../../../store/test-plan/test-plan.selectors';
import { TestPlan } from '../../../shared/models/test-plan.model';
import { TestPlanSummary } from '../../../shared/models/test-plan.model';
import { TestPlanApiService } from '../../../core/services/test-plan-api.service';
import { EntityHistoryComponent } from '../../../shared/components/entity-history/entity-history.component';

@Component({
  selector: 'app-test-plan-detail',
  standalone: true,
  imports: [
    AsyncPipe,
    DecimalPipe,
    LowerCasePipe,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatTableModule,
    MatChipsModule,
    TranslateModule,
    EntityHistoryComponent,
  ],
  templateUrl: './test-plan-detail.component.html',
  styleUrl: './test-plan-detail.component.scss',
})
export class TestPlanDetailComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);
  private readonly testPlanApi = inject(TestPlanApiService);
  private readonly cdr = inject(ChangeDetectorRef);

  projectId = '';
  planId = '';
  testPlan$: Observable<TestPlan | undefined> = of(undefined);
  summary: TestPlanSummary | null = null;
  runColumns = ['name', 'environment', 'status', 'total', 'passed', 'failed'];

  ngOnInit(): void {
    this.projectId = this.route.parent?.snapshot.paramMap.get('id') ?? '';
    this.planId = this.route.snapshot.paramMap.get('planId') ?? '';

    if (this.projectId && this.planId) {
      this.store.dispatch(TestPlanActions.loadTestPlans({ projectId: this.projectId }));
      this.testPlan$ = this.store.select(selectTestPlanById(this.planId));
      this.loadSummary();
    }
  }

  deleteTestPlan(id: string): void {
    this.store.dispatch(TestPlanActions.deleteTestPlan({ projectId: this.projectId, id }));
  }

  private loadSummary(): void {
    this.testPlanApi.getSummary(this.projectId, this.planId).subscribe((summary) => {
      this.summary = summary;
      this.cdr.detectChanges();
    });
  }
}
