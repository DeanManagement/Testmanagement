import { ChangeDetectorRef, Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators, FormsModule } from '@angular/forms';
import { take } from 'rxjs/operators';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { TranslateModule } from '@ngx-translate/core';
import { AsyncPipe } from '@angular/common';
import { TestRunActions } from '../../../store/test-run/test-run.actions';
import { TestPlanActions } from '../../../store/test-plan/test-plan.actions';
import { selectAllTestPlans } from '../../../store/test-plan/test-plan.selectors';
import { ProjectMemberApiService } from '../../../core/services/project-member-api.service';
import { ProjectMember } from '../../../shared/models/project-member.model';
import { TestCaseSummary } from '../../../shared/models/test-suite.model';
import { TestCasePickerComponent } from '../../../shared/components/test-case-picker/test-case-picker.component';
import { FieldErrorComponent } from '../../../shared/components/field-error/field-error.component';
import { CustomFieldsFormComponent } from '../../../shared/components/custom-fields/custom-fields-form.component';
import { CustomFieldValues } from '../../../shared/models/custom-field.model';
import { EnvironmentInputComponent } from '../../../shared/components/environment-input/environment-input.component';
import { EnvironmentApiService } from '../../../core/services/environment-api.service';
import { TestRunApiService } from '../../../core/services/test-run-api.service';
import { ProjectEnvironment } from '../../../shared/models/environment.model';
import { MAX_ENVIRONMENTS_PER_REQUEST } from '../../../shared/models/test-run.model';

@Component({
  selector: 'app-test-run-form',
  standalone: true,
  imports: [
    CustomFieldsFormComponent,
    FieldErrorComponent,
    AsyncPipe,
    ReactiveFormsModule,
    FormsModule,
    RouterLink,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatCardModule,
    MatSelectModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSlideToggleModule,
    TranslateModule,
    EnvironmentInputComponent,
    TestCasePickerComponent,
  ],
  templateUrl: './test-run-form.component.html',
  styleUrl: './test-run-form.component.scss',
})
export class TestRunFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);
  private readonly memberApi = inject(ProjectMemberApiService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);
  private readonly environmentApi = inject(EnvironmentApiService);
  private readonly testRunApi = inject(TestRunApiService);
  private readonly router = inject(Router);

  projectId = '';
  environments: ProjectEnvironment[] = [];
  multiEnvironment = false;
  selectedEnvironmentIds: string[] = [];
  readonly maxEnvironments = MAX_ENVIRONMENTS_PER_REQUEST;
  saving = false;
  selectedCases: TestCaseSummary[] = [];
  members: ProjectMember[] = [];

  testPlans$ = this.store.select(selectAllTestPlans);

  form = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(255)]],
    environment: [''],
    testPlanId: [''],
    executorId: [''],
    customFields: this.fb.control<CustomFieldValues>({}),
  });

  ngOnInit(): void {
    this.projectId = this.route.parent?.snapshot.paramMap.get('id') ?? '';
    if (this.projectId) {
      this.store.dispatch(TestPlanActions.loadTestPlans({ projectId: this.projectId }));
      this.memberApi.getByProject(this.projectId).pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe((m) => {
        this.members = m;
        this.cdr.detectChanges();
      });
      this.environmentApi.getActive(this.projectId).pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe((e) => {
        this.environments = e;
        this.cdr.detectChanges();
      });
    }

    const params = this.route.snapshot.queryParams;
    if (params['testPlanId']) {
      this.form.patchValue({ testPlanId: params['testPlanId'] });
    }
  }

  get canSubmit(): boolean {
    if (this.form.invalid || this.saving) {
      return false;
    }
    return !this.multiEnvironment
      || (this.selectedEnvironmentIds.length > 0 && this.selectedEnvironmentIds.length <= this.maxEnvironments);
  }

  onSubmit(): void {
    if (!this.canSubmit) return;
    this.saving = true;
    if (this.multiEnvironment) {
      this.createAcrossEnvironments();
      return;
    }
    // Saving by name may register a new environment, so the cached list is stale afterwards.
    this.environmentApi.invalidate(this.projectId);

    this.store.dispatch(
      TestRunActions.createTestRun({
        projectId: this.projectId,
        request: {
          name: this.form.value.name!,
          environment: this.form.value.environment || undefined,
          testCaseIds: this.selectedCases.map((tc) => tc.id),
          testPlanId: this.form.value.testPlanId || undefined,
          executorId: this.form.value.executorId || undefined,
          customFields: this.form.value.customFields ?? undefined,
        },
      })
    );
  }

  /** One run per selected environment, then the run list filtered to the new runs' name. */
  private createAcrossEnvironments(): void {
    const name = this.form.value.name!;
    this.testRunApi.createAcrossEnvironments(this.projectId, {
      name,
      testCaseIds: this.selectedCases.map((tc) => tc.id),
      testPlanId: this.form.value.testPlanId || undefined,
      executorId: this.form.value.executorId || undefined,
      environmentIds: this.selectedEnvironmentIds,
      customFields: this.form.value.customFields ?? undefined,
    }).pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => this.router.navigate(['/projects', this.projectId, 'test-runs'], { queryParams: { q: name } }),
      error: () => {
        this.saving = false;
        this.cdr.detectChanges();
      },
    });
  }
}
