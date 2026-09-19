import { ChangeDetectorRef, Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule } from '@ngx-translate/core';
import { filter, switchMap, take } from 'rxjs';
import { SharedStepApiService } from '../../../core/services/shared-step-api.service';
import { TestCaseApiService } from '../../../core/services/test-case-api.service';
import { HasUnsavedChanges } from '../../../core/guards/unsaved-changes.guard';
import { SharedStep, SharedStepStepRequest, SharedStepUsage } from '../../../shared/models/shared-step.model';
import { FieldErrorComponent } from '../../../shared/components/field-error/field-error.component';
import { ConfirmDialogComponent } from '../../../shared/components/confirm-dialog/confirm-dialog.component';
import {
  localStepGroup,
  StepImageState,
  StepListEditorComponent,
  syncStepImages,
} from '../../../shared/components/step-list-editor/step-list-editor.component';

/**
 * Creates or edits one shared step (PRD-030). Saving changes every test case that uses it, so the
 * page says how many do, and lists them.
 */
@Component({
  selector: 'app-shared-step-editor',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    TranslateModule,
    FieldErrorComponent,
    StepListEditorComponent,
  ],
  templateUrl: './shared-step-editor.component.html',
  styleUrl: './shared-step-editor.component.scss',
})
export class SharedStepEditorComponent implements OnInit, HasUnsavedChanges {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(SharedStepApiService);
  private readonly testCaseApi = inject(TestCaseApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  projectId = '';
  sharedStepId: string | null = null;
  usages: SharedStepUsage[] = [];
  saving = false;
  stepImages = new Map<number, StepImageState>();

  readonly form = this.fb.group({
    title: ['', [Validators.required, Validators.maxLength(255)]],
    description: [''],
    steps: this.fb.array([]),
  });

  get steps(): FormArray {
    return this.form.get('steps') as FormArray;
  }

  ngOnInit(): void {
    this.projectId = this.route.parent?.snapshot.paramMap.get('id') ?? '';
    this.sharedStepId = this.route.snapshot.paramMap.get('sharedStepId');
    if (!this.sharedStepId) {
      this.addStep();
      return;
    }
    this.api.get(this.projectId, this.sharedStepId).pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe((sharedStep) => this.load(sharedStep));
    this.api.usages(this.projectId, this.sharedStepId).pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe((usages) => {
        this.usages = usages;
        this.cdr.detectChanges();
      });
  }

  addStep(): void {
    this.steps.push(localStepGroup(this.fb));
  }

  hasUnsavedChanges(): boolean {
    return this.form.dirty && !this.saving;
  }

  save(): void {
    if (this.form.invalid || this.saving) return;
    this.saving = true;
    const request = {
      title: this.form.value.title!.trim(),
      description: this.form.value.description || undefined,
      steps: this.steps.value.map((s: { id: string | null; action: string; expectedResult: string; testData: string }): SharedStepStepRequest => ({
        id: s.id ?? undefined,
        action: s.action,
        expectedResult: s.expectedResult,
        testData: s.testData || undefined,
      })),
    };
    const saved$ = this.sharedStepId
      ? this.api.update(this.projectId, this.sharedStepId, request)
      : this.api.create(this.projectId, request);
    const images = new Map(this.stepImages);
    saved$.pipe(
      switchMap((sharedStep) => syncStepImages(this.testCaseApi, sharedStep.steps, images).pipe(
        switchMap(() => this.api.get(this.projectId, sharedStep.id)))),
      take(1),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe({
      next: (sharedStep) => {
        this.saving = false;
        if (!this.sharedStepId) {
          this.form.markAsPristine();
          this.router.navigate(['/projects', this.projectId, 'shared-steps', sharedStep.id]);
          return;
        }
        this.load(sharedStep);
      },
      error: () => {
        this.saving = false;
        this.cdr.detectChanges();
      },
    });
  }

  /** Refused by the server while test cases use it; the page shows which ones. */
  delete(): void {
    this.dialog.open(ConfirmDialogComponent, {
      data: { titleKey: 'sharedStep.deleteTitle', messageKey: 'sharedStep.deleteConfirm', danger: true },
    }).afterClosed().pipe(
      filter(Boolean),
      switchMap(() => this.api.delete(this.projectId, this.sharedStepId!)),
      take(1),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(() => {
      this.form.markAsPristine();
      this.router.navigate(['/projects', this.projectId, 'shared-steps']);
    });
  }

  private load(sharedStep: SharedStep): void {
    this.form.patchValue({ title: sharedStep.title, description: sharedStep.description ?? '' });
    this.steps.clear();
    this.stepImages.clear();
    [...sharedStep.steps].sort((a, b) => a.orderIndex - b.orderIndex).forEach((step, index) => {
      this.steps.push(localStepGroup(this.fb, step));
      if (step.imageId) {
        this.stepImages.set(index, { id: step.imageId, preview: this.testCaseApi.getStepImageUrl(step.imageId) });
      }
    });
    this.form.markAsPristine();
    this.cdr.detectChanges();
  }
}
