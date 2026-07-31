import { ChangeDetectorRef, Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { ProjectActions } from '../../../store/project/project.actions';
import { selectProjectById } from '../../../store/project/project.selectors';
import { Project } from '../../../shared/models/project.model';
import { HasUnsavedChanges } from '../../../core/guards/unsaved-changes.guard';

@Component({
  selector: 'app-project-form',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatCardModule,
    MatProgressSpinnerModule,
    TranslateModule,
  ],
  templateUrl: './project-form.component.html',
  styleUrl: './project-form.component.scss',
})
export class ProjectFormComponent implements OnInit, HasUnsavedChanges {
  private readonly fb = inject(FormBuilder);
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  editMode = false;
  projectId: string | null = null;
  projectKey: string | null = null;
  saving = false;
  dirty = false;

  form = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(255)]],
    description: [''],
  });

  ngOnInit(): void {
    this.projectId = this.route.snapshot.paramMap.get('id');
    if (this.projectId) {
      this.editMode = true;
      this.store.select(selectProjectById(this.projectId)).pipe(takeUntilDestroyed(this.destroyRef)).subscribe((project: Project | undefined) => {
        if (project) {
          this.projectKey = project.key;
          this.form.patchValue({
            name: project.name,
            description: project.description,
          });
          this.cdr.detectChanges();
        }
      });
    }
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.dirty = false;
    this.saving = true;

    if (this.editMode && this.projectId) {
      this.store.dispatch(
        ProjectActions.updateProject({
          id: this.projectId,
          request: {
            name: this.form.value.name!,
            description: this.form.value.description || undefined,
          },
        })
      );
      this.router.navigate(['/projects', this.projectId]);
    } else {
      this.store.dispatch(
        ProjectActions.createProject({
          request: {
            name: this.form.value.name!,
            description: this.form.value.description || undefined,
          },
        })
      );
    }
  }

  markDirty(): void {
    this.dirty = true;
  }

  hasUnsavedChanges(): boolean {
    return this.dirty && !this.saving;
  }
}
