import { Component, inject, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { AsyncPipe } from '@angular/common';
import { TestRunActions } from '../../../store/test-run/test-run.actions';
import { TestCaseActions } from '../../../store/test-case/test-case.actions';
import { selectAllTestCases } from '../../../store/test-case/test-case.selectors';
import { TestPlanActions } from '../../../store/test-plan/test-plan.actions';
import { selectAllTestPlans } from '../../../store/test-plan/test-plan.selectors';

@Component({
  selector: 'app-test-run-form',
  standalone: true,
  imports: [
    AsyncPipe,
    ReactiveFormsModule,
    RouterLink,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatCardModule,
    MatCheckboxModule,
    MatSelectModule,
    MatIconModule,
    TranslateModule,
  ],
  templateUrl: './test-run-form.component.html',
  styleUrl: './test-run-form.component.scss',
})
export class TestRunFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);

  projectId = '';
  selectedTestCaseIds = new Set<string>();

  testCases$ = this.store.select(selectAllTestCases);
  testPlans$ = this.store.select(selectAllTestPlans);

  form = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(255)]],
    environment: [''],
    testPlanId: [''],
  });

  ngOnInit(): void {
    this.projectId = this.route.parent?.snapshot.paramMap.get('id') ?? '';
    if (this.projectId) {
      this.store.dispatch(TestCaseActions.loadTestCases({ projectId: this.projectId }));
      this.store.dispatch(TestPlanActions.loadTestPlans({ projectId: this.projectId }));
    }
  }

  toggleTestCase(id: string): void {
    if (this.selectedTestCaseIds.has(id)) {
      this.selectedTestCaseIds.delete(id);
    } else {
      this.selectedTestCaseIds.add(id);
    }
  }

  onSubmit(): void {
    if (this.form.invalid) return;

    this.store.dispatch(
      TestRunActions.createTestRun({
        projectId: this.projectId,
        request: {
          name: this.form.value.name!,
          environment: this.form.value.environment || undefined,
          testCaseIds: [...this.selectedTestCaseIds],
          testPlanId: this.form.value.testPlanId || undefined,
        },
      })
    );
  }
}
