import { ChangeDetectorRef, Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { take } from 'rxjs/operators';
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
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatExpansionModule } from '@angular/material/expansion';
import { TranslateModule } from '@ngx-translate/core';
import { TestPlanActions } from '../../../store/test-plan/test-plan.actions';
import { selectTestPlanById } from '../../../store/test-plan/test-plan.selectors';
import { TestPlanStatus } from '../../../shared/models/test-plan.model';
import { ProjectMemberApiService } from '../../../core/services/project-member-api.service';
import { ProjectMember } from '../../../shared/models/project-member.model';
import { HasUnsavedChanges } from '../../../core/guards/unsaved-changes.guard';
import { FieldErrorComponent } from '../../../shared/components/field-error/field-error.component';
import { hasCriteria, toGate } from './release-gate-form';

@Component({
  selector: 'app-test-plan-form',
  standalone: true,
  imports: [
    FieldErrorComponent,
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
    MatProgressSpinnerModule,
    MatExpansionModule,
    TranslateModule,
  ],
  templateUrl: './test-plan-form.component.html',
  styleUrl: './test-plan-form.component.scss',
})
export class TestPlanFormComponent implements OnInit, HasUnsavedChanges {
  private readonly fb = inject(FormBuilder);
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly memberApi = inject(ProjectMemberApiService);
  private readonly destroyRef = inject(DestroyRef);

  editMode = false;
  projectId = '';
  planId: string | null = null;
  saving = false;
  dirty = false;
  statuses: TestPlanStatus[] = ['OPEN', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'];
  members: ProjectMember[] = [];
  /** The gate section starts open when the plan already has one, so nobody misses it's on. */
  gateExpanded = false;

  form = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(255)]],
    description: [''],
    targetDate: [null as Date | null],
    status: [null as TestPlanStatus | null],
    assigneeId: [''],
    // PRD-037: each empty means that criterion is off.
    gate: this.fb.group({
      minPassRate: [null as number | null, [Validators.min(0), Validators.max(100)]],
      maxBlockerBugs: [null as number | null, [Validators.min(0)]],
      minCoverage: [null as number | null, [Validators.min(0), Validators.max(100)]],
      maxFlaky: [null as number | null, [Validators.min(0)]],
    }),
  });

  ngOnInit(): void {
    this.projectId = this.route.parent?.snapshot.paramMap.get('id') ?? '';
    this.planId = this.route.snapshot.paramMap.get('planId');

    if (this.projectId) {
      this.memberApi.getByProject(this.projectId).pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe((m) => {
        this.members = m;
        this.cdr.detectChanges();
      });
    }

    if (this.planId) {
      this.editMode = true;
      this.store.dispatch(TestPlanActions.loadTestPlans({ projectId: this.projectId }));
      this.store.select(selectTestPlanById(this.planId)).pipe(takeUntilDestroyed(this.destroyRef)).subscribe((plan) => {
        if (plan) {
          this.form.patchValue({
            name: plan.name,
            description: plan.description,
            targetDate: plan.targetDate ? new Date(plan.targetDate) : null,
            status: plan.status,
            assigneeId: plan.assigneeId || '',
            gate: plan.gate,
          });
          this.gateExpanded = hasCriteria(plan.gate);
          this.cdr.detectChanges();
        }
      });
    }
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.dirty = false;
    this.form.markAsPristine();
    this.saving = true;

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
            gate: toGate(this.form.getRawValue().gate),
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
            gate: toGate(this.form.getRawValue().gate),
          },
        })
      );
    }
  }


  hasUnsavedChanges(): boolean {
    // form.dirty covers every field; dirty covers what lives outside the form controls and
    // re-arms the guard after a failed save.
    return (this.form.dirty || this.dirty) && !this.saving;
  }

  private formatDate(date: Date): string {
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
  }
}
