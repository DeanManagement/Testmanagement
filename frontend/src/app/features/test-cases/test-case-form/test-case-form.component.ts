import { ChangeDetectorRef, Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatTabsModule } from '@angular/material/tabs';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { HttpErrorResponse } from '@angular/common/http';
import { TestCaseActions } from '../../../store/test-case/test-case.actions';
import { selectTestCaseById } from '../../../store/test-case/test-case.selectors';
import { GherkinPreview, Priority, TestCaseStatus, TestStepRequest } from '../../../shared/models/test-case.model';
import { toGherkin } from './gherkin-text';
import { TestCaseApiService } from '../../../core/services/test-case-api.service';
import { take } from 'rxjs/operators';
import {
  localStepGroup,
  referenceStepGroup,
  StepImageState,
  StepListEditorComponent,
  syncStepImages,
} from '../../../shared/components/step-list-editor/step-list-editor.component';
import { SharedStepPickerDialogComponent } from '../../shared-steps/shared-step-picker-dialog/shared-step-picker-dialog.component';
import { SharedStep } from '../../../shared/models/shared-step.model';
import { MatDialog } from '@angular/material/dialog';
import { MatTooltipModule } from '@angular/material/tooltip';
import { HasUnsavedChanges } from '../../../core/guards/unsaved-changes.guard';
import { FieldErrorComponent } from '../../../shared/components/field-error/field-error.component';
import { CustomFieldsFormComponent } from '../../../shared/components/custom-fields/custom-fields-form.component';
import { CustomFieldValues } from '../../../shared/models/custom-field.model';
import { MAX_ESTIMATE_MINUTES } from '../../../shared/models/effort.model';

import { selectableStatuses } from '../review/review-status';
import { ProjectApiService } from '../../../core/services/project-api.service';

@Component({
  selector: 'app-test-case-form',
  standalone: true,
  imports: [
    CustomFieldsFormComponent,
    FieldErrorComponent,
    ReactiveFormsModule,
    RouterLink,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatTabsModule,
    TranslateModule,
    StepListEditorComponent,
    MatTooltipModule,
  ],
  templateUrl: './test-case-form.component.html',
  styleUrl: './test-case-form.component.scss',
})
export class TestCaseFormComponent implements OnInit, HasUnsavedChanges {
  private readonly fb = inject(FormBuilder);
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly testCaseApi = inject(TestCaseApiService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly projectApi = inject(ProjectApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly translate = inject(TranslateService);
  private readonly dialog = inject(MatDialog);

  editMode = false;
  projectId = '';
  testCaseId: string | null = null;
  saving = false;
  dirty = false;

  priorities: Priority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
  statuses: TestCaseStatus[] = selectableStatuses(false);
  /** PRD-033: under review ACTIVE is set by approving, so the form doesn't offer it. */
  private reviewRequired = false;
  private loadedStatus: TestCaseStatus | null = null;

  stepImages = new Map<number, StepImageState>();

  /** PRD-040 §3.7: the steps as Gherkin text, while the editor is open; null when closed. */
  gherkinText: string | null = null;
  gherkinApplying = false;
  gherkinError: string | null = null;
  /** What the server said it changed or dropped the last time the text was applied. */
  gherkinNotes: string[] = [];

  form = this.fb.group({
    title: ['', [Validators.required, Validators.maxLength(255)]],
    description: [''],
    preconditions: [''],
    priority: ['MEDIUM' as Priority],
    status: ['DRAFT' as TestCaseStatus],
    labels: [''],
    customFields: this.fb.control<CustomFieldValues>({}),
    estimateMinutes: this.fb.control<number | null>(null, [Validators.min(1), Validators.max(MAX_ESTIMATE_MINUTES)]),
    steps: this.fb.array([]),
  });

  get steps(): FormArray {
    return this.form.get('steps') as FormArray;
  }

  ngOnInit(): void {
    this.projectId = this.route.parent?.snapshot.paramMap.get('id') ?? '';
    this.testCaseId = this.route.snapshot.paramMap.get('tcId');
    this.projectApi.getById(this.projectId).pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe((project) => {
      this.reviewRequired = project.reviewRequired;
      this.statuses = selectableStatuses(this.reviewRequired, this.loadedStatus);
      this.cdr.detectChanges();
    });
    const folderId = this.route.snapshot.queryParamMap.get('folderId');

    if (this.testCaseId) {
      this.editMode = true;
      this.store.dispatch(TestCaseActions.loadTestCase({ projectId: this.projectId, id: this.testCaseId }));
      this.store.select(selectTestCaseById(this.testCaseId)).pipe(takeUntilDestroyed(this.destroyRef)).subscribe((tc) => {
        if (tc) {
          this.form.patchValue({
            title: tc.title,
            description: tc.description,
            preconditions: tc.preconditions,
            priority: tc.priority,
            status: tc.status,
            labels: tc.labels?.join(', ') ?? '',
            customFields: tc.customFields ?? {},
            estimateMinutes: tc.estimateMinutes,
          });
          this.loadedStatus = tc.status;
          this.statuses = selectableStatuses(this.reviewRequired, this.loadedStatus);
          this.steps.clear();
          this.stepImages.clear();
          tc.steps?.forEach((step, index) => {
            this.steps.push(step.sharedStepId
              ? referenceStepGroup(this.fb, step.sharedStepId, step.sharedStepTitle ?? step.action, step.expandedSteps ?? [])
              : localStepGroup(this.fb, step));
            if (step.imageId && !step.sharedStepId) {
              this.stepImages.set(index, {
                id: step.imageId,
                preview: this.testCaseApi.getStepImageUrl(step.imageId),
              });
            }
          });
          this.cdr.detectChanges();
        }
      });
    }
  }

  addStep(): void {
    this.steps.push(localStepGroup(this.fb));
  }

  get hasReferences(): boolean {
    return (this.steps.value as { sharedStepId: string | null }[]).some((s) => !!s.sharedStepId);
  }

  /** PRD-030: picks one of the project's shared steps and adds a reference to it at the end. */
  insertSharedStep(): void {
    this.dialog.open(SharedStepPickerDialogComponent, { width: '560px', data: { projectId: this.projectId } })
      .afterClosed().pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe((picked: SharedStep | undefined) => {
        if (picked) {
          this.steps.push(referenceStepGroup(this.fb, picked.id, picked.title, picked.steps));
          this.markDirty();
          this.cdr.detectChanges();
        }
      });
  }

  openGherkin(): void {
    this.gherkinText = toGherkin({
      title: this.form.value.title ?? '',
      description: this.form.value.description ?? null,
      labels: splitLabels(this.form.value.labels ?? ''),
      steps: this.steps.value as TestStepRequest[],
    });
    this.gherkinError = null;
    this.gherkinNotes = [];
  }

  closeGherkin(): void {
    this.gherkinText = null;
    this.gherkinError = null;
  }

  /** True when applying would drop something the steps hold and Gherkin cannot. */
  get gherkinDropsExpectedResults(): boolean {
    return (this.steps.value as TestStepRequest[]).some((s) => !!s.expectedResult?.trim());
  }

  /** The server reads the text (nothing is saved), then the form takes its title, description, tags and steps. */
  applyGherkin(): void {
    if (this.gherkinText === null || this.gherkinApplying) return;
    this.gherkinApplying = true;
    this.gherkinError = null;
    this.testCaseApi.previewGherkin(this.projectId, this.gherkinText)
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (preview) => {
          this.gherkinApplying = false;
          if (preview.problems.length > 0) {
            this.gherkinError = preview.problems.join('; ');
            this.cdr.detectChanges();
            return;
          }
          if (preview.steps.length === 0 && this.steps.length > 0) {
            // Usually keywords of another language read as description; never wipe steps silently.
            this.gherkinError = this.translate.instant('testCase.form.gherkin.noSteps');
            this.cdr.detectChanges();
            return;
          }
          this.takeGherkin(preview);
          this.cdr.detectChanges();
        },
        error: (err: HttpErrorResponse) => {
          this.gherkinApplying = false;
          this.gherkinError = err.error?.message ?? err.message;
          this.cdr.detectChanges();
        },
      });
  }

  private takeGherkin(preview: GherkinPreview): void {
    this.form.patchValue({
      title: preview.title,
      description: preview.description ?? '',
      labels: preview.labels.join(', '),
    });
    this.steps.clear();
    for (const step of preview.steps) {
      this.steps.push(localStepGroup(this.fb, { action: step.action, testData: step.testData ?? '' }));
    }
    const notes = [...preview.warnings];
    if (preview.parameterSets.length > 0) {
      notes.push(this.translate.instant('testCase.form.gherkin.examplesNotSaved'));
    }
    this.gherkinNotes = notes;
    this.gherkinText = null;
    this.markDirty();
  }

  markDirty(): void {
    this.dirty = true;
  }

  hasUnsavedChanges(): boolean {
    return this.dirty && !this.saving;
  }

  onSubmit(): void {
    if (this.form.invalid || this.saving) return;
    this.dirty = false;
    this.saving = true;

    const labels = splitLabels(this.form.value.labels ?? '');
    const steps: TestStepRequest[] = this.steps.value.map((s: { action: string; expectedResult: string; testData: string; sharedStepId: string | null }) =>
      s.sharedStepId
        ? { sharedStepId: s.sharedStepId }
        : { action: s.action, expectedResult: s.expectedResult, testData: s.testData || undefined });

    const request = {
      title: this.form.value.title!,
      description: this.form.value.description || undefined,
      preconditions: this.form.value.preconditions || undefined,
      priority: this.form.value.priority as Priority,
      status: this.form.value.status as TestCaseStatus,
      labels,
      steps,
      customFields: this.form.value.customFields ?? undefined,
    };
    const estimate = this.form.value.estimateMinutes;

    const pendingImages = new Map(this.stepImages);

    if (this.editMode && this.testCaseId) {
      // An emptied estimate is sent as 0, which the server reads as "clear"; omitted would keep it.
      this.testCaseApi.update(this.projectId, this.testCaseId, { ...request, estimateMinutes: estimate ?? 0 }).pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe({
        next: (tc) => {
          syncStepImages(this.testCaseApi, tc.steps, pendingImages).pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe(() => {
            this.store.dispatch(TestCaseActions.updateTestCaseSuccess({ testCase: tc }));
            this.saving = false;
            this.cdr.detectChanges();
            this.router.navigate(['/projects', this.projectId, 'test-cases', this.testCaseId]);
          });
        },
        // Re-arm the unsaved-changes guard after a failed save (PRD-022 §4.5).
        error: () => { this.dirty = true; this.saving = false; this.cdr.detectChanges(); },
      });
    } else {
      const folderId = this.route.snapshot.queryParamMap.get('folderId');
      const createRequest = { ...request, estimateMinutes: estimate ?? undefined, ...(folderId ? { folderId } : {}) };
      this.testCaseApi.create(this.projectId, createRequest).pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe({
        next: (tc) => {
          syncStepImages(this.testCaseApi, tc.steps, pendingImages).pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe(() => {
            this.store.dispatch(TestCaseActions.createTestCaseSuccess({ testCase: tc }));
            this.saving = false;
            this.cdr.detectChanges();
            this.router.navigate(['/projects', this.projectId, 'test-cases', tc.id]);
          });
        },
        // Re-arm the unsaved-changes guard after a failed save (PRD-022 §4.5).
        error: () => { this.dirty = true; this.saving = false; this.cdr.detectChanges(); },
      });
    }
  }

}

function splitLabels(labels: string): string[] {
  return labels.split(',').map((l) => l.trim()).filter((l) => l);
}
