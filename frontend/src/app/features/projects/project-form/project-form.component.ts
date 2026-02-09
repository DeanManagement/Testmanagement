import { Component, inject, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { TranslateModule } from '@ngx-translate/core';
import { ProjectActions } from '../../../store/project/project.actions';
import { selectProjectById } from '../../../store/project/project.selectors';
import { Project } from '../../../shared/models/project.model';

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
    TranslateModule,
  ],
  templateUrl: './project-form.component.html',
  styleUrl: './project-form.component.scss',
})
export class ProjectFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  editMode = false;
  projectId: string | null = null;
  projectKey: string | null = null;

  form = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(255)]],
    description: [''],
  });

  ngOnInit(): void {
    this.projectId = this.route.snapshot.paramMap.get('id');
    if (this.projectId) {
      this.editMode = true;
      this.store.select(selectProjectById(this.projectId)).subscribe((project: Project | undefined) => {
        if (project) {
          this.projectKey = project.key;
          this.form.patchValue({
            name: project.name,
            description: project.description,
          });
        }
      });
    }
  }

  onSubmit(): void {
    if (this.form.invalid) return;

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
}
