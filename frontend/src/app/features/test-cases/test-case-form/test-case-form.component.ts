import { Component, inject, OnInit } from '@angular/core';
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
import { TranslateModule } from '@ngx-translate/core';
import { TestCaseActions } from '../../../store/test-case/test-case.actions';
import { selectTestCaseById } from '../../../store/test-case/test-case.selectors';
import { Priority, TestCase, TestCaseStatus } from '../../../shared/models/test-case.model';
import { TestCaseApiService } from '../../../core/services/test-case-api.service';
import { forkJoin, Observable, of } from 'rxjs';
import { AuthImagePipe } from '../../../shared/pipes/auth-image.pipe';

interface StepImageState {
  id?: string;
  file?: File;
  preview?: string;
  removed?: boolean;
}

@Component({
  selector: 'app-test-case-form',
  standalone: true,
  imports: [
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
    AuthImagePipe,
  ],
  templateUrl: './test-case-form.component.html',
  styleUrl: './test-case-form.component.scss',
})
export class TestCaseFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly testCaseApi = inject(TestCaseApiService);

  editMode = false;
  projectId = '';
  testCaseId: string | null = null;
  saving = false;

  priorities: Priority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
  statuses: TestCaseStatus[] = ['DRAFT', 'ACTIVE', 'DEPRECATED'];

  stepImages = new Map<number, StepImageState>();

  form = this.fb.group({
    title: ['', [Validators.required, Validators.maxLength(255)]],
    description: [''],
    preconditions: [''],
    priority: ['MEDIUM' as Priority],
    status: ['DRAFT' as TestCaseStatus],
    labels: [''],
    steps: this.fb.array([]),
  });

  get steps(): FormArray {
    return this.form.get('steps') as FormArray;
  }

  ngOnInit(): void {
    this.projectId = this.route.parent?.snapshot.paramMap.get('id') ?? '';
    this.testCaseId = this.route.snapshot.paramMap.get('tcId');
    if (this.testCaseId) {
      this.editMode = true;
      this.store.dispatch(TestCaseActions.loadTestCases({ projectId: this.projectId }));
      this.store.select(selectTestCaseById(this.testCaseId)).subscribe((tc) => {
        if (tc) {
          this.form.patchValue({
            title: tc.title,
            description: tc.description,
            preconditions: tc.preconditions,
            priority: tc.priority,
            status: tc.status,
            labels: tc.labels?.join(', ') ?? '',
          });
          this.steps.clear();
          this.stepImages.clear();
          tc.steps?.forEach((step, index) => {
            this.steps.push(
              this.fb.group({
                action: [step.action, Validators.required],
                expectedResult: [step.expectedResult],
                testData: [step.testData],
              })
            );
            if (step.imageId) {
              this.stepImages.set(index, {
                id: step.imageId,
                preview: this.testCaseApi.getStepImageUrl(step.imageId),
              });
            }
          });
        }
      });
    }
  }

  addStep(): void {
    this.steps.push(
      this.fb.group({
        action: ['', Validators.required],
        expectedResult: [''],
        testData: [''],
      })
    );
  }

  removeStep(index: number): void {
    this.steps.removeAt(index);
    this.stepImages.delete(index);
    const updated = new Map<number, StepImageState>();
    this.stepImages.forEach((value, key) => {
      if (key > index) {
        updated.set(key - 1, value);
      } else {
        updated.set(key, value);
      }
    });
    this.stepImages = updated;
  }

  onImageSelected(index: number, event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length) return;
    const file = input.files[0];
    const reader = new FileReader();
    reader.onload = () => {
      this.stepImages.set(index, {
        ...this.stepImages.get(index),
        file,
        preview: reader.result as string,
        removed: false,
      });
    };
    reader.readAsDataURL(file);
    input.value = '';
  }

  removeImage(index: number): void {
    const state = this.stepImages.get(index);
    if (state?.id) {
      this.stepImages.set(index, { id: state.id, removed: true });
    } else {
      this.stepImages.delete(index);
    }
  }

  getImagePreview(index: number): string | undefined {
    const state = this.stepImages.get(index);
    if (state?.removed) return undefined;
    return state?.preview;
  }

  hasImage(index: number): boolean {
    return !!this.getImagePreview(index);
  }

  onSubmit(): void {
    if (this.form.invalid || this.saving) return;
    this.saving = true;

    const labelsStr = this.form.value.labels as string;
    const labels = labelsStr
      ? labelsStr.split(',').map((l) => l.trim()).filter((l) => l)
      : [];
    const steps = this.steps.value.map((s: any) => ({
      action: s.action,
      expectedResult: s.expectedResult,
      testData: s.testData || undefined,
    }));

    const request = {
      title: this.form.value.title!,
      description: this.form.value.description || undefined,
      preconditions: this.form.value.preconditions || undefined,
      priority: this.form.value.priority as Priority,
      status: this.form.value.status as TestCaseStatus,
      labels,
      steps,
    };

    if (this.editMode && this.testCaseId) {
      this.testCaseApi.update(this.projectId, this.testCaseId, request).subscribe({
        next: (tc) => {
          this.store.dispatch(TestCaseActions.updateTestCaseSuccess({ testCase: tc }));
          this.syncImages(tc).subscribe(() => {
            this.saving = false;
            this.router.navigate(['/projects', this.projectId, 'test-cases', this.testCaseId]);
          });
        },
        error: () => { this.saving = false; },
      });
    } else {
      this.testCaseApi.create(this.projectId, request).subscribe({
        next: (tc) => {
          this.store.dispatch(TestCaseActions.createTestCaseSuccess({ testCase: tc }));
          this.syncImages(tc).subscribe(() => {
            this.saving = false;
            this.router.navigate(['/projects', this.projectId, 'test-cases', tc.id]);
          });
        },
        error: () => { this.saving = false; },
      });
    }
  }

  private syncImages(savedTestCase: TestCase): Observable<unknown> {
    const ops: Observable<unknown>[] = [];
    const sortedSteps = [...savedTestCase.steps].sort((a, b) => a.orderIndex - b.orderIndex);

    this.stepImages.forEach((state, index) => {
      const step = sortedSteps[index];
      if (!step) return;

      if (state.removed && state.id) {
        ops.push(this.testCaseApi.deleteStepImage(state.id));
      }
      if (state.file && !state.removed) {
        ops.push(this.testCaseApi.uploadStepImage(step.id, state.file));
      }
    });

    return ops.length ? forkJoin(ops) : of(null);
  }
}
