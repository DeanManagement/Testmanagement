import { ChangeDetectorRef, Component, inject, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatSelectModule } from '@angular/material/select';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { TestPlanActions } from '../../../store/test-plan/test-plan.actions';
import { selectTestPlanById } from '../../../store/test-plan/test-plan.selectors';
import { TestPlanStatus } from '../../../shared/models/test-plan.model';
import { ProjectMemberApiService } from '../../../core/services/project-member-api.service';
import { ProjectMember } from '../../../shared/models/project-member.model';

@Component({
  selector: 'app-test-plan-form',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatCardModule,
    MatSelectModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatIconModule,
    TranslateModule,
  ],
  templateUrl: './test-plan-form.component.html',
  styleUrl: './test-plan-form.component.scss',
})
export class TestPlanFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly memberApi = inject(ProjectMemberApiService);

  editMode = false;
  projectId = '';
  planId: string | null = null;
  statuses: TestPlanStatus[] = ['OPEN', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'];
  members: ProjectMember[] = [];

  form = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(255)]],
    description: [''],
    targetDate: [null as Date | null],
    status: [null as TestPlanStatus | null],
    assigneeId: [''],
  });

  ngOnInit(): void {
    this.projectId = this.route.parent?.snapshot.paramMap.get('id') ?? '';
    this.planId = this.route.snapshot.paramMap.get('planId');

    if (this.projectId) {
      this.memberApi.getByProject(this.projectId).subscribe((m) => {
        this.members = m;
        this.cdr.detectChanges();
      });
    }

    if (this.planId) {
      this.editMode = true;
      this.store.dispatch(TestPlanActions.loadTestPlans({ projectId: this.projectId }));
      this.store.select(selectTestPlanById(this.planId)).subscribe((plan) => {
        if (plan) {
          this.form.patchValue({
            name: plan.name,
            description: plan.description,
            targetDate: plan.targetDate ? new Date(plan.targetDate) : null,
            status: plan.status,
            assigneeId: plan.assigneeId || '',
          });
          this.cdr.detectChanges();
        }
      });
    }
  }

  onSubmit(): void {
    if (this.form.invalid) return;

    const targetDate = this.form.value.targetDate
      ? this.formatDate(this.form.value.targetDate)
      : undefined;

    if (this.editMode && this.planId) {
      this.store.dispatch(
        TestPlanActions.updateTestPlan({
          projectId: this.projectId,
          id: this.planId,
          request: {
            name: this.form.value.name!,
            description: this.form.value.description || undefined,
            status: this.form.value.status || undefined,
            targetDate,
            assigneeId: this.form.value.assigneeId || undefined,
          },
        })
      );
      this.router.navigate(['/projects', this.projectId, 'test-plans', this.planId]);
    } else {
      this.store.dispatch(
        TestPlanActions.createTestPlan({
          projectId: this.projectId,
          request: {
            name: this.form.value.name!,
            description: this.form.value.description || undefined,
            targetDate,
            assigneeId: this.form.value.assigneeId || undefined,
          },
        })
      );
    }
  }

  private formatDate(date: Date): string {
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
  }
}
