import { ChangeDetectorRef, Component, inject, Input } from '@angular/core';
import { FormArray, FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule } from '@ngx-translate/core';
import { forkJoin, Observable, of } from 'rxjs';
import { TestStep } from '../../models/test-case.model';
import { AuthImagePipe } from '../../pipes/auth-image.pipe';
import { EnlargeImageDirective } from '../image-viewer/enlarge-image.directive';
import { FieldErrorComponent } from '../field-error/field-error.component';
import { TestCaseApiService } from '../../../core/services/test-case-api.service';

/** A step image while editing: an existing one, a file chosen but not uploaded yet, or one removed. */
export interface StepImageState {
  id?: string;
  file?: File;
  preview?: string;
  removed?: boolean;
}

/** The form group of a case's own step. */
export function localStepGroup(fb: FormBuilder, step?: Partial<TestStep>): FormGroup {
  return fb.group({
    id: [step?.id ?? null],
    action: [step?.action ?? '', Validators.required],
    expectedResult: [step?.expectedResult ?? ''],
    testData: [step?.testData ?? ''],
    sharedStepId: [null as string | null],
    sharedStepTitle: [null as string | null],
    expandedSteps: [[] as TestStep[]],
  });
}

/**
 * The form group of a reference to a shared step (PRD-030). Its action holds the title so the
 * form stays valid; the server ignores it and resolves the shared step by id.
 */
export function referenceStepGroup(fb: FormBuilder, sharedStepId: string, title: string, steps: TestStep[]): FormGroup {
  return fb.group({
    id: [null as string | null],
    action: [title, Validators.required],
    expectedResult: [''],
    testData: [''],
    sharedStepId: [sharedStepId],
    sharedStepTitle: [title],
    expandedSteps: [steps],
  });
}

/**
 * Uploads chosen images and deletes removed ones after a save. Images are keyed by position, which
 * is also the position of the step in {@code savedSteps}.
 */
export function syncStepImages(api: TestCaseApiService, savedSteps: TestStep[],
                               images: Map<number, StepImageState>): Observable<unknown> {
  const ordered = [...savedSteps].sort((a, b) => a.orderIndex - b.orderIndex);
  const ops: Observable<unknown>[] = [];
  images.forEach((state, index) => {
    const step = ordered[index];
    if (!step) return;
    if (state.removed && state.id) {
      ops.push(api.deleteStepImage(state.id));
    }
    if (state.file && !state.removed) {
      ops.push(api.uploadStepImage(step.id, state.file));
    }
  });
  return ops.length ? forkJoin(ops) : of(null);
}

/**
 * The editable list of steps, shared by the test case form and the shared step editor. A reference
 * to a shared step shows as one collapsed row that expands read-only; its steps are edited on the
 * shared step's own page.
 */
@Component({
  selector: 'app-step-list-editor',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    TranslateModule,
    AuthImagePipe, EnlargeImageDirective,
    FieldErrorComponent,
  ],
  templateUrl: './step-list-editor.component.html',
  styleUrl: './step-list-editor.component.scss',
})
export class StepListEditorComponent {
  private readonly cdr = inject(ChangeDetectorRef);

  @Input({ required: true }) steps!: FormArray;
  /** Mutated in place, so the owner keeps the same map it uploads from after saving. */
  @Input({ required: true }) images!: Map<number, StepImageState>;
  /** For the "edit shared step" link; empty where references cannot occur. */
  @Input() projectId = '';

  private readonly expanded = new WeakSet<object>();

  groupAt(index: number): FormGroup {
    return this.steps.at(index) as FormGroup;
  }

  isReference(index: number): boolean {
    return !!this.groupAt(index).value.sharedStepId;
  }

  isExpanded(index: number): boolean {
    return this.expanded.has(this.groupAt(index));
  }

  toggleExpanded(index: number): void {
    const group = this.groupAt(index);
    if (this.expanded.has(group)) {
      this.expanded.delete(group);
    } else {
      this.expanded.add(group);
    }
  }

  removeStep(index: number): void {
    this.steps.removeAt(index);
    this.steps.markAsDirty();
    // Images are keyed by position: those after the removed step move up one.
    const shifted = new Map<number, StepImageState>();
    this.images.forEach((value, key) => {
      if (key !== index) {
        shifted.set(key > index ? key - 1 : key, value);
      }
    });
    this.images.clear();
    shifted.forEach((value, key) => this.images.set(key, value));
  }

  onImageSelected(index: number, event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length) return;
    const file = input.files[0];
    const reader = new FileReader();
    reader.onload = () => {
      this.images.set(index, { ...this.images.get(index), file, preview: reader.result as string, removed: false });
      this.steps.markAsDirty();
      this.cdr.detectChanges();
    };
    reader.readAsDataURL(file);
    input.value = '';
  }

  removeImage(index: number): void {
    const state = this.images.get(index);
    if (state?.id) {
      this.images.set(index, { id: state.id, removed: true });
    } else {
      this.images.delete(index);
    }
    this.steps.markAsDirty();
  }

  imagePreview(index: number): string | undefined {
    const state = this.images.get(index);
    return state?.removed ? undefined : state?.preview;
  }
}
