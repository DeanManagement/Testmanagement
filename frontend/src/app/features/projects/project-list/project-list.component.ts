import { Component, inject, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { AsyncPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule } from '@ngx-translate/core';
import { map, Observable } from 'rxjs';
import { ProjectActions } from '../../../store/project/project.actions';
import { selectAllProjects, selectProjectsLoading, selectProjectsError } from '../../../store/project/project.selectors';
import { selectIsSystemAdmin } from '../../../store/auth/auth.selectors';
import { Project } from '../../../shared/models/project.model';

@Component({
  selector: 'app-project-list',
  standalone: true,
  imports: [
    AsyncPipe,
    FormsModule,
    RouterLink,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatFormFieldModule,
    MatInputModule,
    TranslateModule,
  ],
  templateUrl: './project-list.component.html',
  styleUrl: './project-list.component.scss',
})
export class ProjectListComponent implements OnInit {
  private readonly store = inject(Store);

  projects$ = this.store.select(selectAllProjects);
  loading$ = this.store.select(selectProjectsLoading);
  error$ = this.store.select(selectProjectsError);
  isAdmin$ = this.store.select(selectIsSystemAdmin);
  displayedColumns = ['key', 'name', 'description', 'actions'];
  searchTerm = '';

  get filteredProjects$(): Observable<Project[]> {
    return this.projects$.pipe(
      map(projects => {
        if (!this.searchTerm) return projects;
        const term = this.searchTerm.toLowerCase();
        return projects.filter(p =>
          p.key.toLowerCase().includes(term) ||
          p.name.toLowerCase().includes(term) ||
          (p.description && p.description.toLowerCase().includes(term))
        );
      })
    );
  }

  ngOnInit(): void {
    this.store.dispatch(ProjectActions.loadProjects());
  }

  retry(): void {
    this.store.dispatch(ProjectActions.loadProjects());
  }

  deleteProject(id: string): void {
    this.store.dispatch(ProjectActions.deleteProject({ id }));
  }
}
