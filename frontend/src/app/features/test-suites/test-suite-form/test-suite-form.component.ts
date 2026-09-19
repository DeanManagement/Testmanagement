import { ChangeDetectorRef, Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { TestSuiteActions } from '../../../store/test-suite/test-suite.actions';
import { selectTestSuiteById } from '../../../store/test-suite/test-suite.selectors';
import { TestCaseSummary } from '../../../shared/models/test-suite.model';
import { TestCasePickerComponent } from '../../../shared/components/test-case-picker/test-case-picker.component';
import { HasUnsavedChanges } from '../../../core/guards/unsaved-changes.guard';
import { FieldErrorComponent } from '../../../shared/components/field-error/field-error.component';

@Component({
  selector: 'app-test-suite-form',
  standalone: true,
  imports: [
    FieldErrorComponent,
    ReactiveFormsModule,
    RouterLink,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatProgressSpinnerModule,
    TranslateModule,
    TestCasePickerComponent,
  ],
  templateUrl: './test-suite-form.component.html',
  styleUrl: './test-suite-form.component.scss',
})
export class TestSuiteFormComponent implements OnInit, HasUnsavedChanges {
  private readonly fb = inject(FormBuilder);
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  editMode = false;
  projectId = '';
  suiteId: string | null = null;
  saving = false;
  dirty = false;
  selectedCases: TestCaseSummary[] = [];

  form = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(255)]],
    description: [''],
  });

  ngOnInit(): void {
    this.projectId = this.route.parent?.snapshot.paramMap.get('id') ?? '';
    this.suiteId = this.route.snapshot.paramMap.get('suiteId');

    if (this.suiteId) {
      this.editMode = true;
      this.store.dispatch(TestSuiteActions.loadTestSuites({ projectId: this.projectId, query: { size: 200 } }));
      this.store.select(selectTestSuiteById(this.suiteId)).pipe(takeUntilDestroyed(this.destroyRef)).subscribe((suite) => {
        if (suite) {
          this.form.patchValue({ name: suite.name, description: suite.description });
          this.selectedCases = suite.testCases ?? [];
          this.cdr.detectChanges();
        }
      });
    }
  }

  selectionChanged(cases: TestCaseSummary[]): void {
    this.selectedCases = cases;
    this.markDirty();
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.dirty = false;
    this.form.markAsPristine();
    this.saving = true;

    const testCaseIds = this.selectedCases.map((tc) => tc.id);

    if (this.editMode && this.suiteId) {
      this.store.dispatch(
        TestSuiteActions.updateTestSuite({
          projectId: this.projectId,
          id: this.suiteId,
          request: {
            name: this.form.value.name!,
            description: this.form.value.description || undefined,
            testCaseIds,
          },
        })
      );
      this.router.navigate(['/projects', this.projectId, 'test-suites', this.suiteId]);
    } else {
      this.store.dispatch(
        TestSuiteActions.createTestSuite({
          projectId: this.projectId,
          request: {
            name: this.form.value.name!,
            description: this.form.value.description || undefined,
            testCaseIds,
          },
        })
      );
    }
  }

  markDirty(): void {
    this.dirty = true;
  }

  hasUnsavedChanges(): boolean {
    // form.dirty covers every field; dirty covers what lives outside the form controls and
    // re-arms the guard after a failed save.
    return (this.form.dirty || this.dirty) && !this.saving;
  }
}
