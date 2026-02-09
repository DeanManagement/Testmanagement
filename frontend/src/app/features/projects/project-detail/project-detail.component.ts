import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { AsyncPipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { TranslateModule } from '@ngx-translate/core';
import { Observable, of } from 'rxjs';
import { ProjectActions } from '../../../store/project/project.actions';
import { selectProjectById } from '../../../store/project/project.selectors';
import { Project } from '../../../shared/models/project.model';

@Component({
  selector: 'app-project-detail',
  standalone: true,
  imports: [
    AsyncPipe,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    TranslateModule,
  ],
  templateUrl: './project-detail.component.html',
  styleUrl: './project-detail.component.scss',
})
export class ProjectDetailComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);

  project$: Observable<Project | undefined> = of(undefined);

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.store.dispatch(ProjectActions.loadProjects());
      this.project$ = this.store.select(selectProjectById(id));
    }
  }

  deleteProject(id: string): void {
    this.store.dispatch(ProjectActions.deleteProject({ id }));
  }
}
