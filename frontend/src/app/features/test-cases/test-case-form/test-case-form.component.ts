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
import { Priority, TestCaseStatus } from '../../../shared/models/test-case.model';

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
  ],
  templateUrl: './test-case-form.component.html',
  styleUrl: './test-case-form.component.scss',
})
export class TestCaseFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  editMode = false;
  projectId = '';
  testCaseId: string | null = null;

  priorities: Priority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
  statuses: TestCaseStatus[] = ['DRAFT', 'ACTIVE', 'DEPRECATED'];

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
          tc.steps?.forEach((step) => {
            this.steps.push(
              this.fb.group({
                action: [step.action, Validators.required],
                expectedResult: [step.expectedResult],
              })
            );
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
      })
    );
  }

  removeStep(index: number): void {
    this.steps.removeAt(index);
  }

  onSubmit(): void {
    if (this.form.invalid) return;

    const labelsStr = this.form.value.labels as string;
    const labels = labelsStr
      ? labelsStr.split(',').map((l) => l.trim()).filter((l) => l)
      : [];
    const steps = this.steps.value;

    if (this.editMode && this.testCaseId) {
      this.store.dispatch(
        TestCaseActions.updateTestCase({
          projectId: this.projectId,
          id: this.testCaseId,
          request: {
            title: this.form.value.title!,
            description: this.form.value.description || undefined,
            preconditions: this.form.value.preconditions || undefined,
            priority: this.form.value.priority as Priority,
            status: this.form.value.status as TestCaseStatus,
            labels,
            steps,
          },
        })
      );
      this.router.navigate(['/projects', this.projectId, 'test-cases', this.testCaseId]);
    } else {
      this.store.dispatch(
        TestCaseActions.createTestCase({
          projectId: this.projectId,
          request: {
            title: this.form.value.title!,
            description: this.form.value.description || undefined,
            preconditions: this.form.value.preconditions || undefined,
            priority: this.form.value.priority as Priority,
            status: this.form.value.status as TestCaseStatus,
            labels,
            steps,
          },
        })
      );
    }
  }
}
